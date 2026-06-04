package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * 캐릭터 상호작용 결과 DTO다.
 *
 * 프론트는 message/interpretation을 즉시 대사로 보여주고,
 * memory가 있으면 새 기억 조각 해금 연출이나 기억 목록에 활용한다.
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
    /**
     * 해금 가능한 LORE/EASTER_EGG 조각의 상세 정보다.
     * COMMON 대사는 기억 목록에 남기지 않으므로 null로 내려갈 수 있다.
     */
    @Builder
    public record Memory(
            String memoryKey,
            String title,
            String storyText
    ) {
    }
}
