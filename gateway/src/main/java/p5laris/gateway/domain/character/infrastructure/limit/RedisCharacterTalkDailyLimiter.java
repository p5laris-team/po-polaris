package p5laris.gateway.domain.character.infrastructure.limit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkDailyLimitProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Redis를 사용해 사용자별 별친구 대화 일일 횟수를 원자적으로 차감한다.
 *
 * 제한 초과나 Redis 장애에서는 AI gRPC 호출 자체를 막아 Gemini 비용 폭주를 방지한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCharacterTalkDailyLimiter {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final DefaultRedisScript<Long> DAILY_LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
                    local limit = tonumber(ARGV[1])
                    local ttl_millis = tonumber(ARGV[2])
                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')

                    if current >= limit then
                        return -1
                    end

                    current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ttl_millis)
                    end

                    return current
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final CharacterTalkDailyLimitProperties properties;
    private Clock clock = Clock.system(SERVICE_ZONE);

    public CharacterTalkLimitResult acquire(Long userId) {
        int dailyLimit = normalizedDailyLimit();
        String resetAt = nextResetAt().toString();

        if (!properties.isEnabled()) {
            return CharacterTalkLimitResult.available(dailyLimit, null, resetAt);
        }
        if (!properties.isRedisBackend() || userId == null || userId <= 0 || dailyLimit <= 0) {
            return unavailableOrOpen(dailyLimit, resetAt);
        }

        try {
            Long currentCount = redisTemplate.execute(
                    DAILY_LIMIT_SCRIPT,
                    List.of(dailyKey(userId)),
                    String.valueOf(dailyLimit),
                    String.valueOf(ttlToNextReset().toMillis())
            );

            if (currentCount == null) {
                return unavailableOrOpen(dailyLimit, resetAt);
            }
            if (currentCount < 0) {
                return CharacterTalkLimitResult.limitExceeded(dailyLimit, resetAt);
            }

            int remainingCount = Math.max(0, dailyLimit - currentCount.intValue());
            return CharacterTalkLimitResult.available(dailyLimit, remainingCount, resetAt);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return handleRedisUnavailable(userId, dailyLimit, resetAt, e);
        } catch (RuntimeException e) {
            return handleRedisUnavailable(userId, dailyLimit, resetAt, e);
        }
    }

    private CharacterTalkLimitResult handleRedisUnavailable(
            Long userId,
            int dailyLimit,
            String resetAt,
            RuntimeException exception
    ) {
        log.warn("별친구 대화 일일 제한 Redis 확인 실패. userId={}, 예외클래스={}",
                userId, exception.getClass().getSimpleName());
        return unavailableOrOpen(dailyLimit, resetAt);
    }

    private CharacterTalkLimitResult unavailableOrOpen(int dailyLimit, String resetAt) {
        if (properties.isFailClosed()) {
            return CharacterTalkLimitResult.temporarilyUnavailable(dailyLimit, resetAt);
        }
        return CharacterTalkLimitResult.available(dailyLimit, null, resetAt);
    }

    private String dailyKey(Long userId) {
        LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
        return "gateway:character-talk:daily:user:%d:date:%s".formatted(userId, today);
    }

    private Duration ttlToNextReset() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
        ZonedDateTime resetAt = now.toLocalDate().plusDays(1).atStartOfDay(SERVICE_ZONE);
        return Duration.between(now, resetAt).plusMinutes(5);
    }

    private ZonedDateTime nextResetAt() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
        return now.toLocalDate().plusDays(1).atStartOfDay(SERVICE_ZONE);
    }

    private int normalizedDailyLimit() {
        return Math.max(0, properties.getDailyLimit());
    }

}
