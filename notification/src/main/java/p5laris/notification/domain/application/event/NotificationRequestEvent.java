package p5laris.notification.domain.application.event;

/**
 * 알림 푸시 요청 이벤트 DTO 입니다.
 * User 모듈의 Outbox 릴레이로부터 'notification-requests' 토픽을 통해 전송받는 데이터 포맷입니다.
 */
public record NotificationRequestEvent(
        
        /** 푸시 수신 대상 사용자 ID */
        Long userId,
        
        /** 알림 푸시 메시지 제목 */
        String title,
        
        /** 알림 푸시 메시지 본문 내용 */
        String body,
        
        /** 알림 종류 (FCM 발송 및 필터링을 위한 Type 문자열) */
        String notificationType
) {}
