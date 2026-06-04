package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * 캐릭터 상태 상세 조회 응답 DTO다.
 */
@Builder
public record CharacterStatusResponse(
        Long characterId,
        States states,
        CharacterGrowthResponse growth
) {
    @Builder
    public record States(
            StateDetail hunger,
            StateDetail energy,
            StateDetail affection
    ) {}

    @Builder
    public record StateDetail(
            int value,
            String label,
            String grade
    ) {}
}
