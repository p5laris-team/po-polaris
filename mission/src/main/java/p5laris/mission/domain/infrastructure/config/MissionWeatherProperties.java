package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 기상청 날씨 context 설정이다.
 *
 * 외부 API key, 기본 격자 좌표, timeout, Redis cache/rate limit 정책은 운영 환경마다 달라질 수 있어 env로 주입한다.
 */
@Component
@ConfigurationProperties(prefix = "mission.weather")
public class MissionWeatherProperties {

    private boolean enabled;
    private String provider;
    private int defaultNx;
    private int defaultNy;
    private String defaultLocationLabel;
    private long timeoutMs;
    private long cacheTtlSeconds;
    private boolean redisCacheEnabled;
    private boolean rateLimitEnabled;
    private int rateLimitRequestsPerMinute;
    private int rateLimitKeyTtlSeconds;
    private boolean rateLimitFailClosed;
    private Kma kma = new Kma();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getDefaultNx() {
        return defaultNx;
    }

    public void setDefaultNx(int defaultNx) {
        this.defaultNx = defaultNx;
    }

    public int getDefaultNy() {
        return defaultNy;
    }

    public void setDefaultNy(int defaultNy) {
        this.defaultNy = defaultNy;
    }

    public String getDefaultLocationLabel() {
        return defaultLocationLabel;
    }

    public void setDefaultLocationLabel(String defaultLocationLabel) {
        this.defaultLocationLabel = defaultLocationLabel;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public boolean isRedisCacheEnabled() {
        return redisCacheEnabled;
    }

    public void setRedisCacheEnabled(boolean redisCacheEnabled) {
        this.redisCacheEnabled = redisCacheEnabled;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getRateLimitRequestsPerMinute() {
        return rateLimitRequestsPerMinute;
    }

    public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
    }

    public int getRateLimitKeyTtlSeconds() {
        return rateLimitKeyTtlSeconds;
    }

    public void setRateLimitKeyTtlSeconds(int rateLimitKeyTtlSeconds) {
        this.rateLimitKeyTtlSeconds = rateLimitKeyTtlSeconds;
    }

    public boolean isRateLimitFailClosed() {
        return rateLimitFailClosed;
    }

    public void setRateLimitFailClosed(boolean rateLimitFailClosed) {
        this.rateLimitFailClosed = rateLimitFailClosed;
    }

    public Kma getKma() {
        return kma;
    }

    public void setKma(Kma kma) {
        this.kma = kma;
    }

    public boolean isKmaProvider() {
        return "kma".equalsIgnoreCase(provider);
    }

    public Duration timeout() {
        return Duration.ofMillis(Math.max(500L, timeoutMs));
    }

    public Duration cacheTtl() {
        return Duration.ofSeconds(Math.max(60L, cacheTtlSeconds));
    }

    public Duration rateLimitKeyTtl() {
        return Duration.ofSeconds(Math.max(70L, rateLimitKeyTtlSeconds));
    }

    public boolean hasDefaultLocation() {
        return defaultNx > 0 && defaultNy > 0
                && defaultLocationLabel != null
                && !defaultLocationLabel.isBlank();
    }

    public boolean hasKmaConfig() {
        return kma != null
                && kma.baseUrl != null
                && !kma.baseUrl.isBlank()
                && kma.serviceKey != null
                && !kma.serviceKey.isBlank();
    }

    public static class Kma {

        private String baseUrl;
        private String serviceKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }
}
