package p5laris.ai.core.exception;

/**
 * AI 모듈의 비즈니스 예외가 공통으로 노출할 에러 코드 계약이다.
 */
public interface ErrorCode {

    String getCode();

    String getMessage();
}
