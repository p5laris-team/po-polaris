package p5laris.gateway.domain.user.exception;

import p5laris.gateway.global.exception.BusinessException;

public class UserGatewayException extends BusinessException {

    public UserGatewayException(UserGatewayErrorCode errorCode) {
        super(errorCode);
    }
}
