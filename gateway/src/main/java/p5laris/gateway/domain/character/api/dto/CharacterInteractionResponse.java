package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

/**
 * 별친구 상호작용 응답이다.
 *
 * message/interpretation은 화면에 바로 보여줄 대사이고,
 * memory는 LORE/EASTER_EGG 조각일 때만 포함된다.
 */
@Builder
public record CharacterInteractionResponse(
        Long characterId,
        String characterTypeCode,
        int level,
        String fragmentType,
        String triggerType,
        String message,
        String interpretation,
        boolean memoryUnlocked,
        boolean alreadyUnlocked,
        Memory memory
) {
    /** 프론트 기억 조각 목록에 넣을 수 있는 해금 조각 상세 정보 */
    @Builder
    public record Memory(
            String memoryKey,
            String title,
            String storyText
    ) {
    }
}
