package p5laris.ai.domain.application.dto;

import java.time.LocalDateTime;

public record CharacterTalkMessageItem(
        String role,
        String content,
        int sequence,
        String requestId,
        boolean fallbackUsed,
        LocalDateTime createdAt,
        String sessionId
) {
}
