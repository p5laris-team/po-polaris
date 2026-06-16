package p5laris.ai.domain.application.generator;

/**
 * AI provider가 응답 metadata로 내려주는 실제 토큰 사용량이다.
 *
 * streaming provider가 usage metadata를 제공하지 않는 경우에는 null을 유지한다.
 * 운영 지표로 오해될 수 있는 추정값은 저장하지 않는다.
 */
public record AiTokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    public static AiTokenUsage empty() {
        return new AiTokenUsage(null, null, null);
    }

    public boolean hasAny() {
        return promptTokens != null || completionTokens != null || totalTokens != null;
    }

    public boolean hasPromptTokens() {
        return promptTokens != null && promptTokens >= 0;
    }

    public boolean hasCompletionTokens() {
        return completionTokens != null && completionTokens >= 0;
    }

    public boolean hasTotalTokens() {
        return totalTokens != null && totalTokens >= 0;
    }

    public int promptTokensOrZero() {
        return hasPromptTokens() ? promptTokens : 0;
    }

    public int completionTokensOrZero() {
        return hasCompletionTokens() ? completionTokens : 0;
    }

    public int totalTokensOrZero() {
        if (hasTotalTokens()) {
            return totalTokens;
        }
        return promptTokensOrZero() + completionTokensOrZero();
    }
}
