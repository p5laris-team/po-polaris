package p5laris.ai.domain.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CharacterTalkDiaryItem(
        LocalDate date,
        String summary,
        Long sourceSessionId,
        LocalDateTime createdAt
) {
}
