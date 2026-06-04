package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

import java.util.Map;


/**
 * Gateway response DTO for API spec 4.4.
 */
@Builder
public record MyCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        String currentAssetUrl,
        Map<String, String> assetUrls,
        boolean active,
        States states,
        CharacterGrowthResponse growth,
        EquippedSkin equippedSkin
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}

    @Builder
    public record EquippedSkin(
            Long itemId,
            String name
    ) {}
}
