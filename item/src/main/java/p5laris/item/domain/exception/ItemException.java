package p5laris.item.domain.exception;

import lombok.Getter;
import p5laris.item.core.exception.BusinessException;
import p5laris.item.core.exception.ErrorCode;

@Getter
public class ItemException extends BusinessException {

    public ItemException(ErrorCode errorCode) {
        super(errorCode);
    }
}
