package p5laris.ai.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.ai.core.exception.ErrorCode;

/**
 * AI 모듈에서 호출자에게 알려줄 수 있는 비즈니스 에러 코드다.
 */
@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    AI_INVALID_REQUEST("AI-001", "AI 미션 생성 요청이 올바르지 않습니다."),
    AI_FALLBACK_INVALID("AI-002", "fallback 미션이 정책 검증을 통과하지 못했습니다."),
    AI_GENERATION_FAILED("AI-003", "AI 미션 생성에 실패했습니다."),
    AI_DUPLICATED_REQUEST("AI-004", "이미 처리된 AI 요청 ID입니다."),
    AI_REQUEST_CONFLICT("AI-005", "같은 AI 요청 ID가 다른 내용으로 재사용되었습니다."),
    AI_EMBEDDING_FAILED("AI-006", "AI 임베딩 생성에 실패했습니다.");

    private final String code;
    private final String message;
}
