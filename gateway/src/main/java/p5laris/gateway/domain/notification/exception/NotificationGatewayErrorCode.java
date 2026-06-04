package p5laris.gateway.domain.notification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import p5laris.gateway.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum NotificationGatewayErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI-001", "알림을 찾을 수 없습니다."),
    INVALID_NOTIFICATION_REQUEST(HttpStatus.BAD_REQUEST, "NOTI-002", "알림 요청 값이 올바르지 않습니다."),
    INVALID_FCM_TOKEN(HttpStatus.BAD_REQUEST, "NOTI-003", "FCM 토큰이 올바르지 않습니다."),
    NOTIFICATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "NOTI-004", "알림 서비스를 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}