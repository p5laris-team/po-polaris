package p5laris.mission.domain.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import p5laris.mission.domain.infrastructure.grpc.NotificationPushClient;

import java.util.Map;

/**
 * 미션 이벤트 중 사용자에게 즉시 알려야 하는 이벤트를 notification 모듈로 전달한다.
 *
 * 알림은 부가 기능이므로 전송 실패가 미션 생성/완료 트랜잭션에 영향을 주지 않도록 커밋 이후 비동기로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionNotificationEventListener {

    private static final String MISSION_OFFERED = "MISSION_OFFERED";
    private static final String MISSION_TITLE_KEY = "missionTitle";

    private final NotificationPushClient notificationPushClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MissionEventLogEvent event) {
        if (!MISSION_OFFERED.equals(event.eventType())) {
            return;
        }

        if (event.userId() == null || event.refId() == null) {
            log.warn("미션 제안 알림 전송 생략. userId 또는 missionId가 없습니다. userId={}, missionId={}",
                    event.userId(), event.refId());
            return;
        }

        try {
            notificationPushClient.sendMissionOfferNotification(
                    event.userId(),
                    event.refId(),
                    readMissionTitle(event.metadata())
            );
        } catch (Exception e) {
            log.warn("미션 제안 알림 전송 실패. userId={}, missionId={}", event.userId(), event.refId(), e);
        }
    }

    private String readMissionTitle(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }

        Object missionTitle = metadata.get(MISSION_TITLE_KEY);
        return missionTitle instanceof String title ? title : null;
    }
}
