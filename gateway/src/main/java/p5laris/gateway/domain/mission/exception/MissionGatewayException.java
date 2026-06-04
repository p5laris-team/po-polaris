package p5laris.gateway.domain.mission.exception;

import p5laris.gateway.global.exception.BusinessException;

/**
 * mission REST API에서 클라이언트에게 알려야 하는 비즈니스 실패를 표현한다.
 */
public class MissionGatewayException extends BusinessException {

    public MissionGatewayException(MissionGatewayErrorCode errorCode) {
        super(errorCode);
    }
}
