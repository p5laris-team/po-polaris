package p5laris.ai.domain.exception;

import lombok.Getter;
import p5laris.ai.domain.domain.enums.AiErrorType;

/**
 * 생성된 문구를 그대로 쓸 수 없어 fallback 문구로 전환해야 할 때 사용하는 내부 예외다.
 *
 * 이 예외는 사용자에게 실패를 노출하기 위한 예외가 아니라, 정상적인 fallback 흐름을 표현한다.
 */
@Getter
public class FallbackRequiredException extends RuntimeException {

    private final AiErrorType errorType;

    public FallbackRequiredException(AiErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
