package p5laris.ai.domain.application.generator;

/**
 * provider streaming chunk와 해당 chunk에 포함될 수 있는 실제 usage metadata를 함께 전달한다.
 */
public record AiChatStreamChunk(
        String text,
        AiTokenUsage tokenUsage
) {

    public static AiChatStreamChunk text(String text) {
        return new AiChatStreamChunk(text, AiTokenUsage.empty());
    }

    public static AiChatStreamChunk usageOnly(AiTokenUsage tokenUsage) {
        return new AiChatStreamChunk("", tokenUsage);
    }
}
