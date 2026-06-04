package p5laris.ai.core.exception;

import lombok.Getter;

/**
 * AI 모듈에서 예측 가능한 비즈니스 실패를 표현하는 기본 예외다.
 *
 * RuntimeException을 직접 던지는 대신 ErrorCode를 함께 들고 다니도록 한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
