package p5laris.character.domain.domain.enums;

import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;

/**
 * 캐릭터 서사 조각을 고를 때 사용하는 트리거다.
 *
 * 현재 PR에서는 프론트 터치/상태 이벤트를 문자열로 받아 이 enum으로 정규화한다.
 */
public enum CharacterStoryTriggerType {
    TAP,
    LEVEL_UP,
    LOW_HUNGER,
    LOW_ENERGY,
    LOW_AFFECTION,
    NIGHT,
    MIDNIGHT;

    /** 요청값이 없으면 기본 터치 상호작용으로 처리한다. */
    public static CharacterStoryTriggerType fromInteractionType(String value) {
        if (value == null || value.isBlank()) {
            return TAP;
        }
        try {
            return CharacterStoryTriggerType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_INTERACTION_TYPE);
        }
    }
}
