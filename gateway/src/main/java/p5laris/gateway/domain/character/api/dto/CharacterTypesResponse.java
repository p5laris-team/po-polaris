package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

import java.util.List;

/**
 * GET /api/character/v1/character-types 응답 DTO
 * API 명세서 §4.1
 */
@Builder
public record CharacterTypesResponse(
        List<CharacterTypeItem> items
) {
    @Builder
    public record CharacterTypeItem(
            long id,
            String code,
            String name,
            String summary,
            String sampleLine,
            int sortOrder
    ) {}
}
