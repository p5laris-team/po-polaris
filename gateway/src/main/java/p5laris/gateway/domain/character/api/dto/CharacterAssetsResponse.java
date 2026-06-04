package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

import java.util.List;

/**
 * GET /api/character/v1/character-types/{characterTypeId}/assets response DTO.
 * API spec 4.2
 */
@Builder
public record CharacterAssetsResponse(
        long characterTypeId,
        List<AssetItem> items
) {
    @Builder
    public record AssetItem(
            String assetType,
            String assetUrl
    ) {}
}
