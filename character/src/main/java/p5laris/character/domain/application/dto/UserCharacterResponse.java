package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * 유저 캐릭터 생성 응답 DTO다.
 */
@Builder
public record UserCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        States states,
        CharacterGrowthResponse growth,
        Instant createdAt
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
