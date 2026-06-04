package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * 돌봄 활동 수행 응답 DTO다.
 */
@Builder
public record CareActionResponse(
        Long careLogId,
        Long characterId,
        String actionType,
        Long consumedItemId,     // 사용한 아이템이 없으면 null
        int consumedQuantity,    // 사용한 아이템이 없으면 0
        States beforeStates,
        States afterStates,
        CharacterGrowthResponse beforeGrowth,
        CharacterGrowthResponse afterGrowth,
        int expGained,
        boolean levelUp,
        String characterMessage
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
