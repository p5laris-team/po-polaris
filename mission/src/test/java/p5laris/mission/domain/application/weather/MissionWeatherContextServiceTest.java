package p5laris.mission.domain.application.weather;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MissionWeatherContextServiceTest {

    private static final Long USER_ID = 1001L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-01T03:10:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Test
    void 날씨_기능이_꺼져_있으면_unavailable_context만_내려준다() throws Exception {
        MissionWeatherProperties properties = properties(false);
        FakeWeatherProvider provider = new FakeWeatherProvider();
        MissionWeatherContextService service = service(properties, provider, FakeWeatherCache.miss(), FakeWeatherRateLimiter.allowed());

        String enriched = service.enrich(USER_ID, baseContextJson());

        Map<String, Object> weatherPolicy = weatherPolicy(enriched);
        assertThat(weatherPolicy.get("available")).isEqualTo(false);
        assertThat(weatherPolicy.get("failureReason")).isEqualTo("DISABLED");
        assertThat(provider.fetchCount).isZero();
    }

    @Test
    void Redis_cache_hit이면_provider와_rate_limit을_호출하지_않고_날씨_context를_붙인다() throws Exception {
        FakeWeatherProvider provider = new FakeWeatherProvider();
        FakeWeatherRateLimiter rateLimiter = FakeWeatherRateLimiter.allowed();
        MissionWeatherContextService service = service(
                properties(true),
                provider,
                FakeWeatherCache.hit(snapshot()),
                rateLimiter
        );

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(true);
        assertThat(weatherPolicy(enriched).get("cacheHit")).isEqualTo(true);
        assertThat(weather(enriched).get("summaryTraits").toString()).contains("RAINY");
        assertThat(provider.fetchCount).isZero();
        assertThat(rateLimiter.callCount).isZero();
    }

    @Test
    void cache_miss이고_rate_limit을_통과하면_provider를_호출하고_cache에_저장한다() throws Exception {
        FakeWeatherProvider provider = new FakeWeatherProvider();
        FakeWeatherCache cache = FakeWeatherCache.miss();
        FakeWeatherRateLimiter rateLimiter = FakeWeatherRateLimiter.allowed();
        MissionWeatherContextService service = service(properties(true), provider, cache, rateLimiter);

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(true);
        assertThat(weatherPolicy(enriched).get("cacheHit")).isEqualTo(false);
        assertThat(provider.fetchCount).isEqualTo(1);
        assertThat(rateLimiter.callCount).isEqualTo(1);
        assertThat(cache.putCount).isEqualTo(1);
    }

    @Test
    void 사용자_선택_권역이면_USER_SELECTED_location_policy를_붙인다() throws Exception {
        WeatherLocation userSelectedLocation = new WeatherLocation(
                60,
                121,
                "경기 남부",
                WeatherLocationSource.USER_SELECTED
        );
        MissionWeatherContextService service = service(
                properties(true),
                new FakeWeatherProvider(),
                FakeWeatherCache.miss(),
                FakeWeatherRateLimiter.allowed(),
                userId -> Optional.of(userSelectedLocation)
        );

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(true);
        assertThat(locationPolicy(enriched).get("locationLabel")).isEqualTo("경기 남부");
        assertThat(locationPolicy(enriched).get("source")).isEqualTo("USER_SELECTED");
        assertThat(weather(enriched).get("summaryTraits").toString()).contains("RAINY");
    }

    @Test
    void Redis_cache를_조회할_수_없으면_provider를_호출하지_않고_unavailable로_닫는다() throws Exception {
        FakeWeatherProvider provider = new FakeWeatherProvider();
        MissionWeatherContextService service = service(
                properties(true),
                provider,
                FakeWeatherCache.unavailable(),
                FakeWeatherRateLimiter.allowed()
        );

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(false);
        assertThat(weatherPolicy(enriched).get("failureReason")).isEqualTo("CACHE_UNAVAILABLE");
        assertThat(provider.fetchCount).isZero();
    }

    @Test
    void rate_limit에_걸리면_provider를_호출하지_않고_unavailable로_닫는다() throws Exception {
        FakeWeatherProvider provider = new FakeWeatherProvider();
        MissionWeatherContextService service = service(
                properties(true),
                provider,
                FakeWeatherCache.miss(),
                FakeWeatherRateLimiter.denied(WeatherFailureReason.RATE_LIMIT)
        );

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(false);
        assertThat(weatherPolicy(enriched).get("failureReason")).isEqualTo("RATE_LIMIT");
        assertThat(provider.fetchCount).isZero();
    }

    @Test
    void provider가_실패하면_날씨_없는_context로_진행한다() throws Exception {
        FakeWeatherProvider provider = new FakeWeatherProvider();
        provider.failureReason = WeatherFailureReason.PROVIDER_TIMEOUT;
        MissionWeatherContextService service = service(
                properties(true),
                provider,
                FakeWeatherCache.miss(),
                FakeWeatherRateLimiter.allowed()
        );

        String enriched = service.enrich(USER_ID, baseContextJson());

        assertThat(weatherPolicy(enriched).get("available")).isEqualTo(false);
        assertThat(weatherPolicy(enriched).get("failureReason")).isEqualTo("PROVIDER_TIMEOUT");
    }

    private MissionWeatherContextService service(
            MissionWeatherProperties properties,
            FakeWeatherProvider provider,
            FakeWeatherCache cache,
            FakeWeatherRateLimiter rateLimiter
    ) {
        return service(
                properties,
                provider,
                cache,
                rateLimiter,
                userId -> Optional.of(new WeatherLocation(60, 127, "SEOUL", WeatherLocationSource.SERVICE_DEFAULT))
        );
    }

    private MissionWeatherContextService service(
            MissionWeatherProperties properties,
            FakeWeatherProvider provider,
            FakeWeatherCache cache,
            FakeWeatherRateLimiter rateLimiter,
            WeatherLocationResolver locationResolver
    ) {
        return new MissionWeatherContextService(
                properties,
                locationResolver,
                cache,
                rateLimiter,
                List.of(provider),
                objectMapper,
                clock
        );
    }

    private MissionWeatherProperties properties(boolean enabled) {
        MissionWeatherProperties properties = new MissionWeatherProperties();
        properties.setEnabled(enabled);
        properties.setProvider("kma");
        properties.setDefaultNx(60);
        properties.setDefaultNy(127);
        properties.setDefaultLocationLabel("SEOUL");
        properties.setTimeoutMs(1500);
        properties.setCacheTtlSeconds(1800);
        properties.setRedisCacheEnabled(true);
        properties.setRateLimitEnabled(true);
        properties.setRateLimitRequestsPerMinute(60);
        properties.setRateLimitKeyTtlSeconds(70);
        properties.setRateLimitFailClosed(true);
        properties.getKma().setBaseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0");
        properties.getKma().setServiceKey("test-key");
        return properties;
    }

    private String baseContextJson() {
        return """
                {"environmentContext":{"date":"2026-06-01","weatherPolicy":{"available":false},"weather":null},"memoryPolicy":{}}
                """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> weatherPolicy(String json) throws Exception {
        Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE_REFERENCE);
        Map<String, Object> environment = (Map<String, Object>) root.get("environmentContext");
        return (Map<String, Object>) environment.get("weatherPolicy");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> locationPolicy(String json) throws Exception {
        Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE_REFERENCE);
        Map<String, Object> environment = (Map<String, Object>) root.get("environmentContext");
        return (Map<String, Object>) environment.get("locationPolicy");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> weather(String json) throws Exception {
        Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE_REFERENCE);
        Map<String, Object> environment = (Map<String, Object>) root.get("environmentContext");
        return (Map<String, Object>) environment.get("weather");
    }

    private WeatherSnapshot snapshot() {
        return snapshot(new WeatherLocation(60, 127, "SEOUL", WeatherLocationSource.SERVICE_DEFAULT));
    }

    private WeatherSnapshot snapshot(WeatherLocation location) {
        return new WeatherSnapshot(
                "KMA",
                location.locationLabel(),
                location.source(),
                location.nx(),
                location.ny(),
                "20260601",
                "1230",
                "20260601",
                "1300",
                22.5d,
                1.0d,
                "RAIN",
                "OVERCAST",
                2.1d,
                List.of("RAINY", "CLOUDY"),
                "2026-06-01T12:45:00"
        );
    }

    private class FakeWeatherProvider implements WeatherProvider {

        private int fetchCount;
        private WeatherFailureReason failureReason;

        @Override
        public boolean supports(String provider) {
            return "kma".equalsIgnoreCase(provider);
        }

        @Override
        public String sourceName() {
            return "KMA";
        }

        @Override
        public String cacheKey(WeatherLocation location, LocalDateTime now) {
            return "weather-cache-key";
        }

        @Override
        public WeatherSnapshot fetch(WeatherLocation location, LocalDateTime now) {
            fetchCount++;
            if (failureReason != null) {
                throw new WeatherProviderException(failureReason, "provider failed");
            }
            return snapshot(location);
        }
    }

    private static class FakeWeatherCache implements WeatherCache {

        private final WeatherCacheLookup lookup;
        private int putCount;

        private FakeWeatherCache(WeatherCacheLookup lookup) {
            this.lookup = lookup;
        }

        private static FakeWeatherCache hit(WeatherSnapshot snapshot) {
            return new FakeWeatherCache(WeatherCacheLookup.hit(snapshot));
        }

        private static FakeWeatherCache miss() {
            return new FakeWeatherCache(WeatherCacheLookup.miss());
        }

        private static FakeWeatherCache unavailable() {
            return new FakeWeatherCache(WeatherCacheLookup.unavailable());
        }

        @Override
        public WeatherCacheLookup get(String cacheKey) {
            return lookup;
        }

        @Override
        public void put(String cacheKey, WeatherSnapshot snapshot) {
            putCount++;
        }
    }

    private static class FakeWeatherRateLimiter implements WeatherRateLimiter {

        private final WeatherRateLimitResult result;
        private int callCount;

        private FakeWeatherRateLimiter(WeatherRateLimitResult result) {
            this.result = result;
        }

        private static FakeWeatherRateLimiter allowed() {
            return new FakeWeatherRateLimiter(WeatherRateLimitResult.allow());
        }

        private static FakeWeatherRateLimiter denied(WeatherFailureReason failureReason) {
            return new FakeWeatherRateLimiter(WeatherRateLimitResult.denied(failureReason));
        }

        @Override
        public WeatherRateLimitResult tryAcquire(String provider) {
            callCount++;
            return result;
        }
    }
}
