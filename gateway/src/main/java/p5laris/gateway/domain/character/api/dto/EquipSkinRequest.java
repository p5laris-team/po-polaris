package p5laris.gateway.domain.character.api.dto;

/**
 * Request DTO for PUT /api/character/v1/characters/{characterId}/equipped-skin
 * API spec 4.8
 */
public record EquipSkinRequest(
        Long itemId  // skin item ID to equip
) {}
