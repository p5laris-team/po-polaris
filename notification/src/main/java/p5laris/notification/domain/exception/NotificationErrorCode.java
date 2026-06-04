package p5laris.notification.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.notification.core.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode  implements ErrorCode {

    NOTIFICATION_NOT_FOUND("NOTI-001", "알림을 찾을 수 없습니다."),
    INVALID_NOTIFICATION_REQUEST("NOTI-002", "알림 요청 값이 올바르지 않습니다."),
    INVALID_FCM_TOKEN("NOTI-003", "FCM 토큰이 올바르지 않습니다.");


    private final String code;
    private final String message;
}
