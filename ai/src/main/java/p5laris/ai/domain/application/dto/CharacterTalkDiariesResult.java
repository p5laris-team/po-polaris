package p5laris.ai.domain.application.dto;

import java.time.LocalDate;
import java.util.List;

public record CharacterTalkDiariesResult(
        Long characterId,
        LocalDate fromDate,
        LocalDate toDate,
        List<CharacterTalkDiaryItem> items
) {
}
