package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShareRewardRecoveryScheduler {

    private final ShareRewardDispatcher shareRewardDispatcher;
    private final ShareRewardOutboxProperties properties;

    @Scheduled(
            fixedDelayString = "${character.share-reward-outbox.fixed-delay-ms}",
            initialDelayString = "${character.share-reward-outbox.initial-delay-ms}"
    )
    public void recoverShareRewards() {
        if (!properties.isEnabled()) {
            return;
        }

        int succeededCount = shareRewardDispatcher.dispatchDue(properties.getBatchSize());
        if (succeededCount > 0) {
            log.info("공유 보상 outbox 재처리를 완료했습니다. succeededCount={}", succeededCount);
        }
    }
}
