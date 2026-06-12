package p5laris.gateway.domain.character.api.dto;

import java.util.List;

public record CharacterTalkDiariesResponse(
        Long characterId,
        String fromDate,
        String toDate,
        List<DiaryItem> items
) {
    public record DiaryItem(
            String date,
            String summary,
            Long sourceSessionId,
            String createdAt
    ) {
    }
}
