package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

/**
 * Response DTO for POST /api/character/v1/characters/{characterId}/care-logs
 * API spec 4.7
 */
@Builder
public record CareActionResponse(
        Long careLogId,
        Long characterId,
        String actionType,
        Consumed consumed,
        States beforeStates,
        States afterStates,
        CharacterGrowthResponse beforeGrowth,
        CharacterGrowthResponse afterGrowth,
        int expGained,
        boolean levelUp,
        String characterMessage
) {
    @Builder
    public record Consumed(
            Long itemId,     // null if no item consumed
            int quantity     // 0 if no item consumed
    ) {}

    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
