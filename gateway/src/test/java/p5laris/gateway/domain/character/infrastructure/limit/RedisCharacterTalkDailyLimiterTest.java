package p5laris.gateway.domain.character.infrastructure.limit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkDailyLimitProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void acquire_disabled_returnsAvailableWithoutRedisCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CharacterTalkDailyLimitProperties properties = properties();
        properties.setEnabled(false);
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties);

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.AVAILABLE, result.talkStatus());
        assertEquals(20, result.dailyLimit());
        assertNull(result.remainingCount());
        verify(redisTemplate, never()).execute(anyScript(), anyList(), any(), any());
    }

    @Test
    void acquire_redisUnavailableAndFailOpen_returnsAvailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        CharacterTalkDailyLimitProperties properties = properties();
        properties.setFailClosed(false);
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties);

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.AVAILABLE, result.talkStatus());
        assertNull(result.remainingCount());
    }

    @Test
    void acquire_invalidUserOrBackend_usesConfiguredFailurePolicy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CharacterTalkDailyLimitProperties failClosed = properties();
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, failClosed);

        assertEquals(
                CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE,
                limiter.acquire(null).talkStatus()
        );
        assertEquals(
                CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE,
                limiter.acquire(0L).talkStatus()
        );

        CharacterTalkDailyLimitProperties failOpen = properties();
        failOpen.setBackend("memory");
        failOpen.setFailClosed(false);
        RedisCharacterTalkDailyLimiter openLimiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, failOpen);
        assertEquals(CharacterTalkLimitStatus.AVAILABLE, openLimiter.acquire(1L).talkStatus());

        verify(redisTemplate, never()).execute(anyScript(), anyList(), any(), any());
    }

    @Test
    void acquire_zeroLimit_isUnavailableWithoutRedisCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CharacterTalkDailyLimitProperties properties = properties();
        properties.setDailyLimit(-10);
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties);

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE, result.talkStatus());
        assertEquals(0, result.dailyLimit());
        verify(redisTemplate, never()).execute(anyScript(), anyList(), any(), any());
    }

    @Test
    void acquire_nullRedisResult_appliesFailClosedPolicy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any())).thenReturn(null);
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties());

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE, result.talkStatus());
        assertNull(result.remainingCount());
    }

    @Test
    void acquire_unexpectedRuntimeException_appliesFailClosedPolicy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(anyScript(), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("script error"));
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties());

        CharacterTalkLimitResult result = limiter.acquire(1L);

        assertEquals(CharacterTalkLimitStatus.TEMPORARILY_UNAVAILABLE, result.talkStatus());
    }

    @Test
    void acquire_usesSeoulDateKeyAndTtlPastMidnight() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                anyScript(),
                eq(List.of("gateway:character-talk:daily:user:7:date:2026-06-12")),
                eq("20"),
                eq("600000")
        )).thenReturn(5L);
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties());
        ReflectionTestUtils.setField(
                limiter,
                "clock",
                Clock.fixed(
                        Instant.parse("2026-06-12T14:55:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );

        CharacterTalkLimitResult result = limiter.acquire(7L);

        assertEquals(CharacterTalkLimitStatus.AVAILABLE, result.talkStatus());
        assertEquals(15, result.remainingCount());
        assertEquals("2026-06-13T00:00+09:00[Asia/Seoul]", result.resetAt());
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
