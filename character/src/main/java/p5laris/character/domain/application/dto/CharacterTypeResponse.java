package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * GET /api/character/v1/character-types 응답 내부 아이템 DTO
 * API 명세서 §4.1
 */
@Builder
public record CharacterTypeResponse(
        Long id,
        String code,
        String name,
        String summary,
        String sampleLine,
        int sortOrder
) {
}
