package p5laris.mission.domain.infrastructure.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.weather.WeatherFailureReason;
import p5laris.mission.domain.application.weather.WeatherRateLimitResult;
import p5laris.mission.domain.application.weather.WeatherRateLimiter;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 기상청 API cache miss 시점에만 사용하는 provider 호출량 제한 장치다.
 *
 * Redis 장애 시 fail-closed이면 날씨 context를 포기하고 미션 생성은 계속 진행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWeatherRateLimiter implements WeatherRateLimiter {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
                    local limit = tonumber(ARGV[1])
                    local ttl_millis = tonumber(ARGV[2])

                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if current >= limit then
                        return 0
                    end

                    current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ttl_millis)
                    end

                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final MissionWeatherProperties properties;
    private final Clock clock;

    @Override
    public WeatherRateLimitResult tryAcquire(String provider) {
        if (!properties.isRateLimitEnabled()) {
            return WeatherRateLimitResult.allow();
        }
        if (properties.getRateLimitRequestsPerMinute() <= 0) {
            return WeatherRateLimitResult.denied(WeatherFailureReason.RATE_LIMIT);
        }

        try {
            Long allowed = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(rateLimitKey(provider)),
                    String.valueOf(properties.getRateLimitRequestsPerMinute()),
                    String.valueOf(properties.rateLimitKeyTtl().toMillis())
            );
            if (!Long.valueOf(1L).equals(allowed)) {
                return WeatherRateLimitResult.denied(WeatherFailureReason.RATE_LIMIT);
            }
            return WeatherRateLimitResult.allow();
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("날씨 API rate limit 저장소를 사용할 수 없습니다. provider={}", provider, e);
            return handleUnavailable();
        } catch (RuntimeException e) {
            log.warn("날씨 API rate limit 확인 중 알 수 없는 예외가 발생했습니다. provider={}", provider, e);
            return handleUnavailable();
        }
    }

    private WeatherRateLimitResult handleUnavailable() {
        if (properties.isRateLimitFailClosed()) {
            return WeatherRateLimitResult.denied(WeatherFailureReason.RATE_LIMIT_UNAVAILABLE);
        }
        return WeatherRateLimitResult.allow();
    }

    private String rateLimitKey(String provider) {
        return "mission:weather:%s:rate-limit:minute:%s".formatted(
                normalize(provider),
                currentWindow()
        );
    }

    private String currentWindow() {
        Instant now = clock.instant();
        long bucketStart = now.getEpochSecond() - (now.getEpochSecond() % 60);
        return WINDOW_FORMATTER.format(Instant.ofEpochSecond(bucketStart));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
    }
}
