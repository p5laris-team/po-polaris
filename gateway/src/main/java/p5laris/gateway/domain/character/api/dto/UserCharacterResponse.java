package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;
import java.time.Instant;

@Builder
public record UserCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        States states,
        CharacterGrowthResponse growth,
        Instant createdAt
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
