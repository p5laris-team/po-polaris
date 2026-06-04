package p5laris.character.domain.application.dto;

import lombok.Builder;

@Builder
public record GrantCharacterExpResponse(
        Long characterId,
        int expGained,
        CharacterGrowthResponse beforeGrowth,
        CharacterGrowthResponse afterGrowth,
        boolean levelUp,
        boolean alreadyProcessed
) {
}
