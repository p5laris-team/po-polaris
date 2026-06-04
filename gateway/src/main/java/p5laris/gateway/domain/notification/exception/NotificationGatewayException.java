package p5laris.gateway.domain.notification.exception;

import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.ErrorCode;

public class NotificationGatewayException extends BusinessException {

    public NotificationGatewayException(ErrorCode errorCode) {
        super(errorCode);
    }
}