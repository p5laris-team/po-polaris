package p5laris.mission.domain.application.guard;

/**
 * AI 후보가 mission 서버 후검증에서 탈락한 이유다.
 *
 * 운영 로그에서 fallback 원인을 빠르게 구분할 수 있도록 프롬프트 실패와 정책 위반을 나눠 둔다.
 */
public enum AiMissionCandidateRejectionReason {
    AI_FALLBACK_USED,
    INVALID_CATEGORY_OR_DIFFICULTY,
    CATEGORY_CHANGED_WITHOUT_POLICY_REASON,
    BLOCKED_CATEGORY,
    BLOCKED_KEYWORD,
    DIFFICULTY_NOT_ALLOWED,
    CHALLENGE_LIMIT_EXCEEDED,
    INVALID_CHALLENGE_VOLUME,
    INVALID_TEXT_LENGTH,
    DUPLICATE_TITLE,
    DUPLICATE_ACTION_FAMILY,
    CHARACTER_TONE_IN_TITLE,
    PROHIBITED_EXPRESSION
}
