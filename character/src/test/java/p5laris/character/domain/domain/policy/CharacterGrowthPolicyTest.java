package p5laris.character.domain.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.character.domain.domain.enums.GrowthStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterGrowthPolicyTest {

    @Test
    @DisplayName("exp 0은 Lv1 BABY 진행률 0으로 계산한다")
    void calculate_expZero_baby() {
        var growth = CharacterGrowthPolicy.calculate(0);

        assertEquals(1, growth.level());
        assertEquals(0, growth.exp());
        assertEquals(0, growth.currentLevelExp());
        assertEquals(200, growth.nextLevelExp());
        assertEquals(200, growth.expToNextLevel());
        assertEquals(0, growth.progressPercent());
        assertEquals(GrowthStage.BABY, growth.growthStage());
        assertEquals("처음 만난 별친구", growth.growthStageLabel());
        assertFalse(growth.maxLevel());
    }

    @Test
    @DisplayName("exp 199는 Lv1 BABY 진행률 99로 계산한다")
    void calculate_exp199_babyProgress99() {
        var growth = CharacterGrowthPolicy.calculate(199);

        assertEquals(1, growth.level());
        assertEquals(GrowthStage.BABY, growth.growthStage());
        assertEquals(1, growth.expToNextLevel());
        assertEquals(99, growth.progressPercent());
        assertFalse(growth.maxLevel());
    }

    @Test
    @DisplayName("exp 200은 Lv2 GROWING 진행률 0으로 계산한다")
    void calculate_exp200_growing() {
        var growth = CharacterGrowthPolicy.calculate(200);

        assertEquals(2, growth.level());
        assertEquals(200, growth.currentLevelExp());
        assertEquals(600, growth.nextLevelExp());
        assertEquals(400, growth.expToNextLevel());
        assertEquals(0, growth.progressPercent());
        assertEquals(GrowthStage.GROWING, growth.growthStage());
        assertEquals("조금 자란 별친구", growth.growthStageLabel());
        assertFalse(growth.maxLevel());
    }

    @Test
    @DisplayName("exp 599는 Lv2 GROWING 진행률 99로 계산한다")
    void calculate_exp599_growingProgress99() {
        var growth = CharacterGrowthPolicy.calculate(599);

        assertEquals(2, growth.level());
        assertEquals(GrowthStage.GROWING, growth.growthStage());
        assertEquals(1, growth.expToNextLevel());
        assertEquals(99, growth.progressPercent());
        assertFalse(growth.maxLevel());
    }

    @Test
    @DisplayName("exp 600 이상은 Lv3 MATURE 만렙으로 계산한다")
    void calculate_exp600_matureMaxLevel() {
        var growth = CharacterGrowthPolicy.calculate(600);

        assertEquals(3, growth.level());
        assertEquals(600, growth.currentLevelExp());
        assertEquals(600, growth.nextLevelExp());
        assertEquals(0, growth.expToNextLevel());
        assertEquals(100, growth.progressPercent());
        assertEquals(GrowthStage.MATURE, growth.growthStage());
        assertEquals("반짝이는 동반자", growth.growthStageLabel());
        assertTrue(growth.maxLevel());
    }

    @Test
    @DisplayName("음수 exp는 0으로 보정한다")
    void calculate_negativeExp_treatedAsZero() {
        var growth = CharacterGrowthPolicy.calculate(-10);

        assertEquals(1, growth.level());
        assertEquals(0, growth.exp());
        assertEquals(0, growth.progressPercent());
    }
}
