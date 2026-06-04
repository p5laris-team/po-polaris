package p5laris.gateway.domain.character.api.dto;

/**
 * Request DTO for POST /api/character/v1/characters/{characterId}/care-logs
 * API spec 4.7
 */
public record CareActionRequest(
        String actionType,  // FEED | SLEEP | PLAY
        Long itemId         // null if no item used
) {}
