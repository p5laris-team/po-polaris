package p5laris.character.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "character.share-reward-outbox")
public class ShareRewardOutboxProperties {

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
