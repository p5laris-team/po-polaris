package p5laris.character.domain.domain.enums;

/**
 * 별친구 서사 조각의 성격을 구분한다.
 *
 * COMMON은 반복 노출 가능한 일반 대사이고, LORE/EASTER_EGG는 유저별 해금 기록을 남긴다.
 */
public enum CharacterStoryFragmentType {
    COMMON,
    LORE,
    EASTER_EGG;

    /** 기억 조각 목록에 남겨야 하는 타입인지 판단한다. */
    public boolean unlockable() {
        return this == LORE || this == EASTER_EGG;
    }
}
