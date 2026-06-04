package p5laris.ai.domain.exception;

import p5laris.ai.core.exception.BusinessException;

/**
 * AI 모듈의 도메인 예외다.
 */
public class AiException extends BusinessException {

    public AiException(AiErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public AiErrorCode getErrorCode() {
        return (AiErrorCode) super.getErrorCode();
    }
}
