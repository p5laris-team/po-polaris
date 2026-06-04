package p5laris.character.domain.application;

import org.springframework.stereotype.Component;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import java.time.LocalDateTime;

@Component
public class ShareRewardBackoffPolicy {

    private final ShareRewardOutboxProperties properties;

    public ShareRewardBackoffPolicy(ShareRewardOutboxProperties properties) {
        this.properties = properties;
    }

    public LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCountAfterFailure) {
        int safeAttemptCount = Math.max(1, attemptCountAfterFailure);
        int exponent = Math.min(safeAttemptCount - 1, 5);
        long delaySeconds = Math.min(
                properties.getRetryMaxDelaySeconds(),
                properties.getRetryInitialDelaySeconds() * (1L << exponent)
        );
        return now.plusSeconds(delaySeconds);
    }
}
