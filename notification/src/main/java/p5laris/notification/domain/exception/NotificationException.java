package p5laris.notification.domain.exception;

import lombok.Getter;
import p5laris.notification.core.exception.BusinessException;
import p5laris.notification.core.exception.ErrorCode;

@Getter
public class NotificationException extends BusinessException {

    public NotificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
