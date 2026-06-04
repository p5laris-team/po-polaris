package p5laris.character.domain.infrastructure.grpc;

import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.NotificationType;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import com.p5laris.proto.notification.v1.TargetType;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import p5laris.character.domain.domain.enums.CharacterMood;

/**
 * character 모듈에서 notification 모듈로 알림 저장과 FCM 발송을 요청하는 gRPC adapter다.
 *
 * 사용자 수신 설정, 방해금지 시간, FCM 토큰 검증은 notification 모듈이 최종 판단한다.
 */
@Component
public class NotificationPushClient {

    private static final String DEFAULT_CHARACTER_NAME = "별친구";
    private static final String SHARE_REWARD_TITLE = "공유 보상 지급 완료";

    @GrpcClient("notification")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    /**
     * 캐릭터 상태가 나빠졌을 때 사용자에게 돌봄을 유도하는 CARE 알림을 요청한다.
     */
    public void sendCharacterStateNotification(
            Long userId,
            Long characterId,
            String characterName,
            CharacterMood mood
    ) {
        NotificationMessage message = toCharacterStateMessage(displayName(characterName), mood);

        notificationStub.sendPushNotification(
                SendPushNotificationRequest.newBuilder()
                        .setUserId(userId)
                        .setTitle(message.title())
                        .setBody(message.body())
                        .setNotificationType(NotificationType.NOTIFICATION_TYPE_CARE)
                        .setTargetType(TargetType.TARGET_TYPE_CHARACTER)
                        .setTargetId(characterId)
                        .build()
        );
    }

    /**
     * 공유 보상이 재처리로 뒤늦게 지급 완료되었을 때 사용자에게 안내한다.
     * 즉시 지급 성공은 완료 화면에서 이미 보여주므로 재처리 성공에만 사용한다.
     */
    public void sendShareRewardCompletedNotification(
            Long userId,
            Long shareLogId,
            int rewardStarPiece
    ) {
        notificationStub.sendPushNotification(
                SendPushNotificationRequest.newBuilder()
                        .setUserId(userId)
                        .setTitle(SHARE_REWARD_TITLE)
                        .setBody("별조각 " + rewardStarPiece + "개가 도착했어요.")
                        .setNotificationType(NotificationType.NOTIFICATION_TYPE_SHARE)
                        .setTargetType(TargetType.TARGET_TYPE_SHARE)
                        .setTargetId(shareLogId)
                        .build()
        );
    }

    private NotificationMessage toCharacterStateMessage(String characterName, CharacterMood mood) {
        return switch (mood) {
            case HUNGRY -> new NotificationMessage(
                    characterName + "가 배고파 보여요",
                    "별사탕밥을 챙겨 줄까요?"
            );
            case LOW_ENERGY -> new NotificationMessage(
                    characterName + "가 졸려 보여요",
                    "구름 베개로 쉬게 해 줄까요?"
            );
            case LONELY -> new NotificationMessage(
                    characterName + "가 조금 심심해 보여요",
                    "별빛 장난감으로 놀아 줄까요?"
            );
            default -> new NotificationMessage(
                    characterName + "의 상태를 확인해 주세요",
                    "별친구가 돌봄을 기다리고 있어요."
            );
        };
    }

    private String displayName(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return DEFAULT_CHARACTER_NAME;
        }

        return characterName.trim();
    }

    private record NotificationMessage(String title, String body) {
    }
}
