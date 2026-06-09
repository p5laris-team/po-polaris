package p5laris.notification.domain.application.event;

/**
 * 알림 푸시 요청 이벤트 DTO 입니다.
 *
 * V1 payload는 userId/title/body/notificationType만 포함했고,
 * V2 payload는 알림 클릭 이동을 위해 targetType/targetId를 선택적으로 포함한다.
 */
public record NotificationRequestEvent(
        
        /** 푸시 수신 대상 사용자 ID */
        Long userId,
        
        /** 알림 푸시 메시지 제목 */
        String title,
        
        /** 알림 푸시 메시지 본문 내용 */
        String body,
        
        /** 알림 종류 (FCM 발송 및 필터링을 위한 Type 문자열) */
        String notificationType,

        /** 알림 클릭 시 이동할 대상 종류 */
        String targetType,

        /** 알림 클릭 시 이동할 대상 ID */
        Long targetId
) {}
