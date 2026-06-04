package p5laris.user.domain.exception;

import lombok.Getter;
import p5laris.user.core.exception.BusinessException;
import p5laris.user.core.exception.ErrorCode;

@Getter
public class UserException extends BusinessException {

    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
