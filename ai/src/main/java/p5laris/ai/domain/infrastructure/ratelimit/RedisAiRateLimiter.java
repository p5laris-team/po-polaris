package p5laris.ai.domain.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiRateLimitProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Redis를 중앙 카운터로 사용하는 AI provider 호출량 제한 구현체다.
 *
 * 여러 ai 서버 인스턴스가 떠도 같은 Redis key를 증가시키므로 전체 Gemini/OpenAI 호출량을 함께 제한할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class RedisAiRateLimiter implements AiRateLimiter {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
                    local provider_limit = tonumber(ARGV[1])
                    local user_limit = tonumber(ARGV[2])
                    local ttl_millis = tonumber(ARGV[3])

                    local provider_current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if provider_current >= provider_limit then
                        return 0
                    end

                    local user_current = tonumber(redis.call('GET', KEYS[2]) or '0')
                    if user_current >= user_limit then
                        return 0
                    end

                    provider_current = redis.call('INCR', KEYS[1])
                    if provider_current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ttl_millis)
                    end

                    user_current = redis.call('INCR', KEYS[2])
                    if user_current == 1 then
                        redis.call('PEXPIRE', KEYS[2], ttl_millis)
                    end

                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final AiRateLimitProperties rateLimitProperties;
    private final Clock clock = Clock.systemUTC();

    @Override
    public void checkAllowed(Long userId, AiProviderType providerType, String model) {
        if (!rateLimitProperties.isEnabled() || rateLimitProperties.isNoopBackend()) {
            return;
        }
        if (!rateLimitProperties.isRedisBackend()) {
            handleUnavailable();
            return;
        }
        if (rateLimitProperties.getProviderRequestsPerMinute() <= 0
                || rateLimitProperties.getUserRequestsPerMinute() <= 0) {
            throw new FallbackRequiredException(AiErrorType.RATE_LIMIT, "AI provider rate limit 설정값이 0 이하입니다.");
        }

        try {
            Long allowed = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(providerKey(providerType, model), userKey(userId, providerType)),
                    String.valueOf(rateLimitProperties.getProviderRequestsPerMinute()),
                    String.valueOf(rateLimitProperties.getUserRequestsPerMinute()),
                    String.valueOf(rateLimitProperties.keyTtl().toMillis())
            );

            if (!Long.valueOf(1L).equals(allowed)) {
                throw new FallbackRequiredException(AiErrorType.RATE_LIMIT, "AI provider rate limit을 초과했습니다.");
            }
        } catch (FallbackRequiredException e) {
            throw e;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            handleUnavailable();
        } catch (RuntimeException e) {
            handleUnavailable();
        }
    }

    private void handleUnavailable() {
        if (rateLimitProperties.isFailClosed()) {
            throw new FallbackRequiredException(
                    AiErrorType.RATE_LIMIT_UNAVAILABLE,
                    "AI provider rate limit 저장소를 사용할 수 없습니다."
            );
        }
    }

    private String providerKey(AiProviderType providerType, String model) {
        return "ai:rate-limit:provider:%s:model:%s:minute:%s".formatted(
                normalize(providerType.name()),
                normalize(model),
                currentWindow()
        );
    }

    private String userKey(Long userId, AiProviderType providerType) {
        return "ai:rate-limit:user:%d:provider:%s:minute:%s".formatted(
                userId,
                normalize(providerType.name()),
                currentWindow()
        );
    }

    private String currentWindow() {
        Instant now = clock.instant();
        long windowSeconds = Math.max(1, rateLimitProperties.getWindowSeconds());
        long bucketStart = now.getEpochSecond() - (now.getEpochSecond() % windowSeconds);
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
