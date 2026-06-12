package p5laris.gateway.domain.character.api.dto;

import java.util.List;

public record CharacterTalkMessagesResponse(
        Long characterId,
        String date,
        String latestSessionId,
        List<MessageItem> messages
) {
    public record MessageItem(
            String role,
            String content,
            int sequence,
            String requestId,
            boolean fallbackUsed,
            String createdAt,
            String sessionId
    ) {
    }
}
