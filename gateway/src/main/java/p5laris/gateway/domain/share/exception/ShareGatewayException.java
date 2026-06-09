package p5laris.gateway.domain.share.exception;

import p5laris.gateway.global.exception.BusinessException;

public class ShareGatewayException extends BusinessException {
    public ShareGatewayException(ShareGatewayErrorCode errorCode) {
        super(errorCode);
    }
}
