package p5laris.ai.domain.application.generator;

/**
 * provider 응답 본문과 응답 metadata의 실제 token usage를 함께 전달한다.
 */
public record AiChatResponse(
        String content,
        AiTokenUsage tokenUsage
) {

    public AiChatResponse {
        tokenUsage = tokenUsage != null ? tokenUsage : AiTokenUsage.empty();
    }
}
