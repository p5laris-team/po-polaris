package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 외부 AI provider 호출량 제한 설정을 env/application.yaml에서 읽어오는 클래스다.
 *
 * Gemini/OpenAI는 유료 API이므로 호출 직전에 Redis 기반 rate limit을 확인한다.
 * 요청 제한 수치와 fail-closed 정책은 운영 환경마다 달라질 수 있어 env로 명시해 주입한다.
 */
@ConfigurationProperties(prefix = "ai.rate-limit")
public class AiRateLimitProperties {

    private boolean enabled;
    private String backend;
    private int providerRequestsPerMinute;
    private int userRequestsPerMinute;
    private int windowSeconds;
    private int keyTtlSeconds;
    private boolean failClosed;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getProviderRequestsPerMinute() {
        return providerRequestsPerMinute;
    }

    public void setProviderRequestsPerMinute(int providerRequestsPerMinute) {
        this.providerRequestsPerMinute = providerRequestsPerMinute;
    }

    public int getUserRequestsPerMinute() {
        return userRequestsPerMinute;
    }

    public void setUserRequestsPerMinute(int userRequestsPerMinute) {
        this.userRequestsPerMinute = userRequestsPerMinute;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getKeyTtlSeconds() {
        return keyTtlSeconds;
    }

    public void setKeyTtlSeconds(int keyTtlSeconds) {
        this.keyTtlSeconds = keyTtlSeconds;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public boolean isRedisBackend() {
        return "redis".equalsIgnoreCase(backend);
    }

    public boolean isNoopBackend() {
        return "none".equalsIgnoreCase(backend) || "noop".equalsIgnoreCase(backend);
    }

    public Duration keyTtl() {
        return Duration.ofSeconds(Math.max(keyTtlSeconds, windowSeconds + 1L));
    }
}
