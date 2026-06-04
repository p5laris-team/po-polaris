package p5laris.mission.domain.infrastructure.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.weather.WeatherCache;
import p5laris.mission.domain.application.weather.WeatherCacheLookup;
import p5laris.mission.domain.application.weather.WeatherSnapshot;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWeatherCache implements WeatherCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MissionWeatherProperties properties;

    @Override
    public WeatherCacheLookup get(String cacheKey) {
        if (!properties.isRedisCacheEnabled()) {
            return WeatherCacheLookup.miss();
        }

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null || cached.isBlank()) {
                return WeatherCacheLookup.miss();
            }
            return WeatherCacheLookup.hit(objectMapper.readValue(cached, WeatherSnapshot.class));
        } catch (JsonProcessingException e) {
            log.warn("날씨 Redis cache 파싱 실패. cacheKey={}", cacheKey, e);
            return WeatherCacheLookup.miss();
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("날씨 Redis cache 조회 실패. cacheKey={}", cacheKey, e);
            return WeatherCacheLookup.unavailable();
        } catch (RuntimeException e) {
            log.warn("날씨 Redis cache 조회 중 알 수 없는 예외가 발생했습니다. cacheKey={}", cacheKey, e);
            return WeatherCacheLookup.unavailable();
        }
    }

    @Override
    public void put(String cacheKey, WeatherSnapshot snapshot) {
        if (!properties.isRedisCacheEnabled()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(snapshot),
                    properties.cacheTtl()
            );
        } catch (JsonProcessingException e) {
            log.warn("날씨 Redis cache 직렬화 실패. cacheKey={}", cacheKey, e);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("날씨 Redis cache 저장 실패. cacheKey={}", cacheKey, e);
        } catch (RuntimeException e) {
            log.warn("날씨 Redis cache 저장 중 알 수 없는 예외가 발생했습니다. cacheKey={}", cacheKey, e);
        }
    }
}
