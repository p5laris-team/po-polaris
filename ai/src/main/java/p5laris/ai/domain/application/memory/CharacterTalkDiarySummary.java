package p5laris.ai.domain.application.memory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CharacterTalkDiarySummary(
        LocalDate date,
        String summary,
        Long sourceSessionId,
        LocalDateTime createdAt
) {
}
