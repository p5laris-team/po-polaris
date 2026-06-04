package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

@Builder
public record CharacterStatusResponse(
        Long characterId,
        States states,
        CharacterGrowthResponse growth
) {
    @Builder
    public record States(
            StateDetail hunger,
            StateDetail energy,
            StateDetail affection
    ) {}

    @Builder
    public record StateDetail(
            int value,
            String label,
            String grade
    ) {}
}
