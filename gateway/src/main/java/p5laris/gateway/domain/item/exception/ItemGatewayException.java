package p5laris.gateway.domain.item.exception;

import p5laris.gateway.global.exception.BusinessException;

/**
 * item REST API에서 클라이언트에게 알려야 하는 비즈니스 실패를 표현한다.
 */
public class ItemGatewayException extends BusinessException {

    public ItemGatewayException(ItemGatewayErrorCode errorCode) {
        super(errorCode);
    }
}
