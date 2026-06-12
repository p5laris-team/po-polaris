package p5laris.mission.domain.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * mission 모듈의 알림 요청을 Kafka로 발행하는 adapter다.
 *
 * 알림 저장, 사용자 설정 확인, 방해금지 시간, FCM 전송 여부는 notification 모듈의 책임으로 둔다.
 * mission 입장에서는 커밋 이후 부가 이벤트로 발행하고, 발행 실패는 핵심 미션 흐름으로 전파하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionNotificationKafkaPublisher {

    private static final String NOTIFICATION_REQUEST_TOPIC = "notification-requests";
    private static final String NOTIFICATION_TYPE_MISSION = "MISSION";
    private static final String TARGET_TYPE_MISSION = "MISSION";
    private static final String MISSION_OFFER_TITLE = "새 미션이 도착했어요";
    private static final String DEFAULT_MISSION_OFFER_BODY = "새 미션을 해볼까요?";
    private static final String MISSION_REWARD_RECOVERED_TITLE = "별조각 지급이 완료됐어요";
    private static final String MISSION_OFFER_NOTIFICATION_KEY_PREFIX = "MISSION_OFFER_NOTIFICATION:";
    private static final String MISSION_REWARD_RECOVERED_NOTIFICATION_KEY_PREFIX = "MISSION_REWARD_RECOVERED_NOTIFICATION:";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendMissionOfferNotification(
            Long userId,
            Long missionId,
            String missionTitle
    ) {
        send(
                MISSION_OFFER_NOTIFICATION_KEY_PREFIX + missionId,
                new MissionNotificationRequestEvent(
                        userId,
                        MISSION_OFFER_TITLE,
                        toMissionOfferBody(missionTitle),
                        NOTIFICATION_TYPE_MISSION,
                        TARGET_TYPE_MISSION,
                        missionId
                )
        );
    }

    public void sendMissionRewardRecoveredNotification(
            Long userId,
            Long missionId,
            int rewardStarPiece
    ) {
        send(
                MISSION_REWARD_RECOVERED_NOTIFICATION_KEY_PREFIX + missionId,
                new MissionNotificationRequestEvent(
                        userId,
                        MISSION_REWARD_RECOVERED_TITLE,
                        toMissionRewardRecoveredBody(rewardStarPiece),
                        NOTIFICATION_TYPE_MISSION,
                        TARGET_TYPE_MISSION,
                        missionId
                )
        );
    }

    private void send(String idempotencyKey, MissionNotificationRequestEvent event) {
        try {
            kafkaTemplate.send(NOTIFICATION_REQUEST_TOPIC, idempotencyKey, event);
        } catch (Exception e) {
            log.warn("미션 알림 요청 Kafka 발행 실패. userId={}, targetType={}, targetId={}, idempotencyKey={}",
                    event.userId(), event.targetType(), event.targetId(), idempotencyKey, e);
        }
    }

    private String toMissionOfferBody(String missionTitle) {
        if (missionTitle == null || missionTitle.isBlank()) {
            return DEFAULT_MISSION_OFFER_BODY;
        }

        return missionTitle.trim() + " 미션을 해볼까요?";
    }

    private String toMissionRewardRecoveredBody(int rewardStarPiece) {
        return "미션 보상 " + rewardStarPiece + " 별조각이 방금 들어왔어요.";
    }
}
