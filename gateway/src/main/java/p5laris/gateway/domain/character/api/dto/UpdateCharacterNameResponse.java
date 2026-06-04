package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;
import java.time.Instant;

@Builder
public record UpdateCharacterNameResponse(
        Long id,
        String name,
        Instant updatedAt
) {}
