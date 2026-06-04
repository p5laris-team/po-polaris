package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 외부 AI provider 서킷 브레이커 설정을 env/application.yaml에서 읽어오는 클래스다.
 *
 * Gemini 장애가 반복될 때 매 요청마다 timeout을 기다리지 않고, 잠시 외부 호출을 차단한 뒤 기존 fallback으로 전환한다.
 */
@ConfigurationProperties(prefix = "ai.circuit-breaker")
public class AiCircuitBreakerProperties {

    private boolean enabled;
    private int slidingWindowSize;
    private int minimumNumberOfCalls;
    private float failureRateThreshold;
    private long slowCallDurationMs;
    private float slowCallRateThreshold;
    private long waitDurationOpenMs;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(int slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public int getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(float failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public long getSlowCallDurationMs() {
        return slowCallDurationMs;
    }

    public void setSlowCallDurationMs(long slowCallDurationMs) {
        this.slowCallDurationMs = slowCallDurationMs;
    }

    public float getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    public void setSlowCallRateThreshold(float slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
    }

    public long getWaitDurationOpenMs() {
        return waitDurationOpenMs;
    }

    public void setWaitDurationOpenMs(long waitDurationOpenMs) {
        this.waitDurationOpenMs = waitDurationOpenMs;
    }

    public int normalizedSlidingWindowSize() {
        return Math.max(2, slidingWindowSize);
    }

    public int normalizedMinimumNumberOfCalls() {
        return Math.min(Math.max(1, minimumNumberOfCalls), normalizedSlidingWindowSize());
    }

    public float normalizedFailureRateThreshold() {
        return normalizePercent(failureRateThreshold);
    }

    public Duration slowCallDuration() {
        return Duration.ofMillis(Math.max(100, slowCallDurationMs));
    }

    public float normalizedSlowCallRateThreshold() {
        return normalizePercent(slowCallRateThreshold);
    }

    public Duration waitDurationOpen() {
        return Duration.ofMillis(Math.max(1_000, waitDurationOpenMs));
    }

    private float normalizePercent(float value) {
        if (value <= 0) {
            return 1;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }
}
