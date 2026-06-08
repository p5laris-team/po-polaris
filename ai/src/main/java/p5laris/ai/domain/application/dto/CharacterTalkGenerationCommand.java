package p5laris.ai.domain.application.dto;

/**
 * 별친구 대화 생성 요청 command다.
 *
 * provider 호출에 필요한 사용자 입력과 세션 기반 대화 맥락을 함께 전달한다.
 */
public record CharacterTalkGenerationCommand(
        Long userId,
        Long characterId,
        String characterType,
        String characterName,
        String userMessage,
        String interactionType,
        String characterContextJson,
        String requestId,
        String sessionId,
        String conversationHistoryJson,
        String memoryContextJson
) {

    public CharacterTalkGenerationCommand(
            Long userId,
            Long characterId,
            String characterType,
            String characterName,
            String userMessage,
            String interactionType,
            String characterContextJson,
            String requestId
    ) {
        this(userId, characterId, characterType, characterName, userMessage, interactionType,
                characterContextJson, requestId, "", "[]", "[]");
    }

    public CharacterTalkGenerationCommand withConversationContext(
            String sessionId,
            String conversationHistoryJson,
            String memoryContextJson
    ) {
        return new CharacterTalkGenerationCommand(
                userId,
                characterId,
                characterType,
                characterName,
                userMessage,
                interactionType,
                characterContextJson,
                requestId,
                sessionId,
                conversationHistoryJson,
                memoryContextJson
        );
    }
}
