package p5laris.gateway.domain.character.infrastructure.limit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkDailyLimitProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCharacterTalkDailyLimiterTest {

    @Test
    @DisplayName("별친구 대화 일일 제한 - 제한 미만이면 남은 횟수를 반환")
    void acquire_available_returnsRemainingCount() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any()))
                .thenReturn(1L);
        RedisCharacterTalkDailyLimiter limiter = new RedisCharacterTalkDailyLimiter(redisTemplate, properties());

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.AVAILABLE, result.talkStatus());
        assertEquals(20, result.dailyLimit());
        assertEquals(19, result.remainingCount());
        assertFalse(result.resetAt().isBlank());
    }

    @Test
    @DisplayName("별친구 대화 일일 제한 - 제한 초과면 AI 호출 불가 상태를 반환")
    void acquire_limitExceeded_returnsLimitStatus() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any()))
                .thenReturn(-1L);
        RedisCharacterTalkDailyLimiter limiter = new RedisCharacterTalkDailyLimiter(redisTemplate, properties());

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.LIMIT_EXCEEDED, result.talkStatus());
        assertEquals(0, result.remainingCount());
    }

    @Test
    @DisplayName("별친구 대화 일일 제한 - Redis 장애 시 fail-closed로 일시 사용 불가 반환")
    void acquire_redisUnavailable_returnsTemporarilyUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        RedisCharacterTalkDailyLimiter limiter = new RedisCharacterTalkDailyLimiter(redisTemplate, properties());

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE, result.talkStatus());
        assertNull(result.remainingCount());
    }

    private CharacterTalkDailyLimitProperties properties() {
        CharacterTalkDailyLimitProperties properties = new CharacterTalkDailyLimitProperties();
        properties.setEnabled(true);
        properties.setBackend("redis");
        properties.setDailyLimit(20);
        properties.setFailClosed(true);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<Long> anyScript() {
        return any(DefaultRedisScript.class);
    }
}
