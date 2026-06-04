package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 캐릭터 상태 알림을 주기적으로 점검하는 스케줄러다.
 *
 * 실행 여부와 주기는 env로 조절해 운영 상황에 맞게 끄거나 늦출 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "character.state-notification",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class CharacterStateNotificationScheduler {

    private final CharacterStateNotificationService characterStateNotificationService;

    @Value("${character.state-notification.batch-size}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${character.state-notification.fixed-delay-ms}",
            initialDelayString = "${character.state-notification.initial-delay-ms}"
    )
    public void dispatchDueStateNotifications() {
        try {
            int requestedCount = characterStateNotificationService.dispatchDueStateNotifications(batchSize);
            log.info("캐릭터 상태 알림 점검 완료. requestedCount={}", requestedCount);
        } catch (Exception e) {
            log.warn("캐릭터 상태 알림 스케줄러 실행 실패", e);
        }
    }
}
