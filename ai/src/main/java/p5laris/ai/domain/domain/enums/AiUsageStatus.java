package p5laris.ai.domain.domain.enums;

/**
 * ai_usage_logs.status에 저장되는 provider 사용 결과 상태다.
 */
public enum AiUsageStatus {
    SUCCESS,
    FAILED,
    FALLBACK,
    RATE_LIMITED
}
