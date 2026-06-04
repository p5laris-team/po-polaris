package p5laris.gateway.domain.character.api.dto;

public record CreateCharacterRequest(
        Long characterTypeId,
        String name
) {}
