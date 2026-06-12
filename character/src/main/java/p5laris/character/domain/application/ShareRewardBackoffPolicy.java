package p5laris.character.domain.application;

import org.springframework.stereotype.Component;
import p5laris.common.outbox.OutboxBackoffPolicy;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import java.time.LocalDateTime;

@Component
public class ShareRewardBackoffPolicy {

    private final ShareRewardOutboxProperties properties;

    public ShareRewardBackoffPolicy(ShareRewardOutboxProperties properties) {
        this.properties = properties;
    }

    public LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCountAfterFailure) {
        return OutboxBackoffPolicy.nextAttemptAt(
                now,
                attemptCountAfterFailure,
                properties.getRetryInitialDelaySeconds(),
                properties.getRetryMaxDelaySeconds()
        );
    }
}
