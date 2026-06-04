package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for PUT /api/character/v1/characters/{characterId}/equipped-skin
 * API spec 4.8
 */
@Builder
public record EquipSkinResponse(
        Long characterId,
        EquippedSkin equippedSkin,
        Instant updatedAt
) {
    @Builder
    public record EquippedSkin(
            Long itemId,
            String name  // TODO [Item Domain Integration]: fetched from item service
    ) {}
}
