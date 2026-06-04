package p5laris.character.domain.domain.policy;

import p5laris.character.domain.domain.enums.ActionType;

/**
 * 돌봄 활동으로 얻는 캐릭터 성장 경험치 정책.
 *
 * FEED/SLEEP/PLAY 각각 하루 3회까지만 경험치를 인정해
 * 아이템을 과하게 소모해야 성장하는 구조가 되지 않도록 제한한다.
 */
public final class CharacterCareGrowthPolicy {

    public static final int EXP_PER_CARE_ACTION = 5;
    public static final int DAILY_EXP_LIMIT_PER_ACTION = 3;

    private CharacterCareGrowthPolicy() {
    }

    public static int calculateExpGained(ActionType actionType, int earnedCountToday) {
        if (actionType == null || earnedCountToday >= DAILY_EXP_LIMIT_PER_ACTION) {
            return 0;
        }
        return EXP_PER_CARE_ACTION;
    }
}
