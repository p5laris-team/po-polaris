package p5laris.mission.domain.application.weather;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 기존 미션 생성 context JSON에 날씨 context를 덧붙인다.
 *
 * 기상청/Redis 장애는 미션 생성 실패가 아니라 개인화 품질 저하로 처리하고,
 * weatherPolicy.available=false를 명시해 AI가 날씨를 추측하지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionWeatherContextService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String WEATHER_UNAVAILABLE_INSTRUCTION = "날씨 정보를 사용할 수 없으므로 날씨 기반 미션을 만들지 않는다.";
    private static final String WEATHER_AVAILABLE_INSTRUCTION = "weather.summaryTraits를 참고하되, 지역명이나 수치 자체를 과하게 노출하지 않고 1~5분 안의 안전한 미션으로만 반영한다.";
    private static final String LOCATION_WEATHER_LOOKUP_INSTRUCTION = "이 위치는 날씨 조회 기준이며, 지역명 자체를 미션 제목이나 설명에 직접 노출하지 않는다.";

    private final MissionWeatherProperties properties;
    private final WeatherLocationResolver weatherLocationResolver;
    private final WeatherCache weatherCache;
    private final WeatherRateLimiter weatherRateLimiter;
    private final List<WeatherProvider> weatherProviders;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public String enrich(Long userId, String recentMissionContextJson) {
        if (!properties.isEnabled()) {
            return mergeUnavailable(recentMissionContextJson, "NONE", WeatherFailureReason.DISABLED, null);
        }

        Optional<WeatherProvider> provider = findProvider();
        if (provider.isEmpty()) {
            return mergeUnavailable(recentMissionContextJson, providerSource(), WeatherFailureReason.UNSUPPORTED_PROVIDER, null);
        }

        Optional<WeatherLocation> location = weatherLocationResolver.resolve(userId);
        if (location.isEmpty()) {
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), WeatherFailureReason.INVALID_CONFIG, null);
        }

        if (!properties.hasKmaConfig()) {
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), WeatherFailureReason.INVALID_CONFIG, location.get());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String cacheKey = provider.get().cacheKey(location.get(), now);
        WeatherCacheLookup cacheLookup = weatherCache.get(cacheKey);
        if (!cacheLookup.available()) {
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), WeatherFailureReason.CACHE_UNAVAILABLE, location.get());
        }
        if (cacheLookup.snapshot().isPresent()) {
            return mergeAvailable(recentMissionContextJson, cacheLookup.snapshot().get(), true);
        }

        WeatherRateLimitResult rateLimit = weatherRateLimiter.tryAcquire(provider.get().sourceName());
        if (!rateLimit.allowed()) {
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), rateLimit.failureReason(), location.get());
        }

        try {
            WeatherSnapshot snapshot = provider.get().fetch(location.get(), now);
            weatherCache.put(cacheKey, snapshot);
            return mergeAvailable(recentMissionContextJson, snapshot, false);
        } catch (WeatherProviderException e) {
            log.warn("날씨 provider 호출 실패. provider={}, userId={}, reason={}",
                    provider.get().sourceName(), userId, e.failureReason());
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), e.failureReason(), location.get());
        } catch (RuntimeException e) {
            log.warn("날씨 context 생성 중 알 수 없는 예외가 발생했습니다. provider={}, userId={}",
                    provider.get().sourceName(), userId, e);
            return mergeUnavailable(recentMissionContextJson, provider.get().sourceName(), WeatherFailureReason.PROVIDER_ERROR, location.get());
        }
    }

    private Optional<WeatherProvider> findProvider() {
        return weatherProviders.stream()
                .filter(provider -> provider.supports(properties.getProvider()))
                .findFirst();
    }

    private String mergeAvailable(String recentMissionContextJson, WeatherSnapshot snapshot, boolean cacheHit) {
        try {
            Map<String, Object> root = readRoot(recentMissionContextJson);
            Map<String, Object> environmentContext = environmentContext(root);
            environmentContext.put("locationPolicy", availableLocationPolicy(snapshot));
            environmentContext.put("weatherPolicy", availableWeatherPolicy(snapshot, cacheHit));
            environmentContext.put("weather", weatherContext(snapshot, cacheHit));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("날씨 context 병합 실패. provider={}, cacheHit={}", snapshot.provider(), cacheHit, e);
            return recentMissionContextJson;
        }
    }

    private String mergeUnavailable(
            String recentMissionContextJson,
            String source,
            WeatherFailureReason failureReason,
            WeatherLocation location
    ) {
        try {
            Map<String, Object> root = readRoot(recentMissionContextJson);
            Map<String, Object> environmentContext = environmentContext(root);
            if (location != null) {
                environmentContext.put("locationPolicy", availableLocationPolicy(location));
            }
            environmentContext.put("weatherPolicy", unavailableWeatherPolicy(source, failureReason));
            environmentContext.put("weather", null);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("날씨 unavailable context 병합 실패. source={}, failureReason={}", source, failureReason, e);
            return recentMissionContextJson;
        }
    }

    private Map<String, Object> readRoot(String recentMissionContextJson) throws Exception {
        JsonNode root = objectMapper.readTree(recentMissionContextJson);
        return objectMapper.convertValue(root, MAP_TYPE_REFERENCE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> environmentContext(Map<String, Object> root) {
        Object existingEnvironment = root.get("environmentContext");
        if (existingEnvironment instanceof Map<?, ?> existingMap) {
            Map<String, Object> environment = new LinkedHashMap<>();
            existingMap.forEach((key, value) -> {
                if (key instanceof String stringKey) {
                    environment.put(stringKey, value);
                }
            });
            root.put("environmentContext", environment);
            return environment;
        }

        Map<String, Object> environment = new LinkedHashMap<>();
        root.put("environmentContext", environment);
        return environment;
    }

    private Map<String, Object> availableLocationPolicy(WeatherSnapshot snapshot) {
        return availableLocationPolicy(new WeatherLocation(
                snapshot.nx(),
                snapshot.ny(),
                snapshot.locationLabel(),
                snapshot.locationSource()
        ));
    }

    private Map<String, Object> availableLocationPolicy(WeatherLocation location) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("available", true);
        context.put("source", location.source().name());
        context.put("locationLabel", location.locationLabel());
        context.put("nx", location.nx());
        context.put("ny", location.ny());
        context.put("instruction", LOCATION_WEATHER_LOOKUP_INSTRUCTION);
        return context;
    }

    private Map<String, Object> availableWeatherPolicy(WeatherSnapshot snapshot, boolean cacheHit) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("available", true);
        context.put("source", snapshot.provider());
        context.put("cacheHit", cacheHit);
        context.put("baseDate", snapshot.baseDate());
        context.put("baseTime", snapshot.baseTime());
        context.put("instruction", WEATHER_AVAILABLE_INSTRUCTION);
        return context;
    }

    private Map<String, Object> unavailableWeatherPolicy(String source, WeatherFailureReason failureReason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("available", false);
        context.put("source", source);
        context.put("failureReason", failureReason.name());
        context.put("instruction", WEATHER_UNAVAILABLE_INSTRUCTION);
        return context;
    }

    private Map<String, Object> weatherContext(WeatherSnapshot snapshot, boolean cacheHit) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("provider", snapshot.provider());
        context.put("forecastDate", snapshot.forecastDate());
        context.put("forecastTime", snapshot.forecastTime());
        context.put("temperatureC", snapshot.temperatureC());
        context.put("precipitationMm", snapshot.precipitationMm());
        context.put("precipitationType", snapshot.precipitationType());
        context.put("sky", snapshot.sky());
        context.put("windSpeedMs", snapshot.windSpeedMs());
        context.put("summaryTraits", snapshot.summaryTraits());
        context.put("cacheHit", cacheHit);
        context.put("fetchedAt", snapshot.fetchedAt());
        return context;
    }

    private String providerSource() {
        if (properties.getProvider() == null || properties.getProvider().isBlank()) {
            return "NONE";
        }
        return properties.getProvider().trim().toUpperCase();
    }
}
