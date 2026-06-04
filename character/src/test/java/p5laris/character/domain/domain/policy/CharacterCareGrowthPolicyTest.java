package p5laris.character.domain.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.character.domain.domain.enums.ActionType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterCareGrowthPolicyTest {

    @Test
    @DisplayName("돌봄 경험치는 액션별 하루 3회 전까지 5 EXP 지급")
    void calculateExpGained_beforeDailyLimit() {
        assertEquals(5, CharacterCareGrowthPolicy.calculateExpGained(ActionType.FEED, 0));
        assertEquals(5, CharacterCareGrowthPolicy.calculateExpGained(ActionType.SLEEP, 1));
        assertEquals(5, CharacterCareGrowthPolicy.calculateExpGained(ActionType.PLAY, 2));
    }

    @Test
    @DisplayName("돌봄 경험치는 액션별 하루 3회부터 0 EXP 지급")
    void calculateExpGained_afterDailyLimit() {
        assertEquals(0, CharacterCareGrowthPolicy.calculateExpGained(ActionType.FEED, 3));
        assertEquals(0, CharacterCareGrowthPolicy.calculateExpGained(ActionType.FEED, 4));
    }

    @Test
    @DisplayName("액션 타입이 없으면 경험치를 지급하지 않음")
    void calculateExpGained_withoutActionType() {
        assertEquals(0, CharacterCareGrowthPolicy.calculateExpGained(null, 0));
    }
}
