package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * 스킨 장착 응답 DTO다.
 */
@Builder
public record EquipSkinResponse(
        Long characterId,
        Long equippedSkinId,
        Instant updatedAt
) {}
