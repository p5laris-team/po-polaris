package p5laris.mission.domain.exception;

import p5laris.mission.core.exception.BusinessException;

public class MissionException extends BusinessException {

    public MissionException(MissionErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public MissionErrorCode getErrorCode() {
        return (MissionErrorCode) super.getErrorCode();
    }
}
