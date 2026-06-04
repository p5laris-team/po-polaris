package p5laris.ai.domain.domain.enums;

/**
 * prompt_templates.category 컬럼에 저장되는 프롬프트 용도다.
 *
 * COMPLETION_QA는 "완료 질문 프롬프트" 용도이며,
 * user_missions.status의 ANSWERING 상태명과는 다른 개념이다.
 */
public enum PromptCategory {
    MISSION_GENERATION,
    CHARACTER_TONE,
    COMPLETION_QA,
    FALLBACK
}
