package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.NotificationType;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import com.p5laris.proto.notification.v1.TargetType;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * mission 모듈에서 notification 모듈로 앱 내부 알림 생성과 FCM 푸시 발송을 요청하는 gRPC adapter다.
 *
 * 알림 저장, 사용자 알림 설정, 방해금지 시간, 실제 FCM 전송 여부는 notification 모듈의 책임으로 둔다.
 */
@Component
public class NotificationPushClient {

    private static final String MISSION_OFFER_TITLE = "새 미션이 도착했어요";
    private static final String DEFAULT_MISSION_OFFER_BODY = "새 미션을 해볼까요?";
    private static final String MISSION_REWARD_RECOVERED_TITLE = "별조각 지급이 완료됐어요";

    @GrpcClient("notification")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    /**
     * 새 미션 제안 알림을 notification 모듈에 위임한다.
     *
     * targetType/targetId를 함께 보내면 프론트가 알림 클릭 시 해당 미션 상세로 이동할 수 있다.
     */
    public void sendMissionOfferNotification(
            Long userId,
            Long missionId,
            String missionTitle
    ) {
        notificationStub.sendPushNotification(
                SendPushNotificationRequest.newBuilder()
                        .setUserId(userId)
                        .setTitle(MISSION_OFFER_TITLE)
                        .setBody(toMissionOfferBody(missionTitle))
                        .setNotificationType(NotificationType.NOTIFICATION_TYPE_MISSION)
                        .setTargetType(TargetType.TARGET_TYPE_MISSION)
                        .setTargetId(missionId)
                        .build()
        );
    }

    /**
     * 미션 보상이 즉시 지급되지 못하고 outbox 재처리로 나중에 성공했을 때 사용자에게 안내한다.
     */
    public void sendMissionRewardRecoveredNotification(
            Long userId,
            Long missionId,
            int rewardStarPiece
    ) {
        notificationStub.sendPushNotification(
                SendPushNotificationRequest.newBuilder()
                        .setUserId(userId)
                        .setTitle(MISSION_REWARD_RECOVERED_TITLE)
                        .setBody(toMissionRewardRecoveredBody(rewardStarPiece))
                        .setNotificationType(NotificationType.NOTIFICATION_TYPE_MISSION)
                        .setTargetType(TargetType.TARGET_TYPE_MISSION)
                        .setTargetId(missionId)
                        .build()
        );
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
