package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * 캐릭터 에셋 응답 아이템 DTO다.
 */
@Builder
public record CharacterAssetResponse(
        String assetType,
        String assetUrl
) {
}
