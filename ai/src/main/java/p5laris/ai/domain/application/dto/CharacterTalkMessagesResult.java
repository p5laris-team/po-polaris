package p5laris.ai.domain.application.dto;

import java.time.LocalDate;
import java.util.List;

public record CharacterTalkMessagesResult(
        Long characterId,
        LocalDate date,
        String latestSessionId,
        List<CharacterTalkMessageItem> messages
) {
}
