package p5laris.mission.domain.application.event;

/**
 * mission 모듈이 notification-requests 토픽으로 발행하는 알림 요청 payload다.
 *
 * notification 모듈의 Kafka consumer는 모듈 간 DTO 패키지 의존을 피하기 위해
 * 같은 필드명을 가진 JSON payload를 자체 DTO로 역직렬화한다.
 */
public record MissionNotificationRequestEvent(
        Long userId,
        String title,
        String body,
        String notificationType,
        String targetType,
        Long targetId
) {
}
