package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mission.reward-outbox")
public class MissionRewardOutboxProperties {

    /*
     * 미션 보상 outbox 스케줄러 설정이다.
     *
     * 실제 값은 application.yaml을 통해 env에서 주입받는다.
     * getter에서 최소값을 보정하는 이유는 잘못된 env 값 때문에 스케줄러가 0ms 반복되거나
     * 재시도 정책이 즉시 무한 반복되는 상황을 막기 위해서다.
     */
    private boolean enabled;
    private long fixedDelayMs;
    private long initialDelayMs;
    private int batchSize;
    private int maxAttempts;
    private long processingTimeoutSeconds;
    private long retryInitialDelaySeconds;
    private long retryMaxDelaySeconds;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return Math.max(1_000L, fixedDelayMs);
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getInitialDelayMs() {
        return Math.max(0L, initialDelayMs);
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public int getBatchSize() {
        return Math.max(1, batchSize);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getProcessingTimeoutSeconds() {
        return Math.max(10L, processingTimeoutSeconds);
    }

    public void setProcessingTimeoutSeconds(long processingTimeoutSeconds) {
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    public long getRetryInitialDelaySeconds() {
        return Math.max(1L, retryInitialDelaySeconds);
    }

    public void setRetryInitialDelaySeconds(long retryInitialDelaySeconds) {
        this.retryInitialDelaySeconds = retryInitialDelaySeconds;
    }

    public long getRetryMaxDelaySeconds() {
        return Math.max(getRetryInitialDelaySeconds(), retryMaxDelaySeconds);
    }

    public void setRetryMaxDelaySeconds(long retryMaxDelaySeconds) {
        this.retryMaxDelaySeconds = retryMaxDelaySeconds;
    }
}
