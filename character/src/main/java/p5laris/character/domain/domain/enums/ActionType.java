package p5laris.character.domain.domain.enums;

/**
 * 캐릭터 돌봄 액션 유형 (AGENTS.md §20.2)
 *
 * FEED  → fullness(포만감) 회복
 * SLEEP → energy(에너지) 회복
 * PLAY  → affection(애정도) 회복
 *
 * 상태는 100을 초과하지 않는다.
 */
public enum ActionType {

    /** 밥 주기: fullness 회복 */
    FEED,

    /** 재우기: energy 회복 */
    SLEEP,

    /** 놀기: affection 회복 */
    PLAY;

    /** 이 액션이 회복하는 상태 필드명 (로그/디버깅 용) */
    public String targetStat() {
        return switch (this) {
            case FEED  -> "fullness";
            case SLEEP -> "energy";
            case PLAY  -> "affection";
        };
    }
}
