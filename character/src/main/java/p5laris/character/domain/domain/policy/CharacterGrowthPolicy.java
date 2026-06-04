package p5laris.character.domain.domain.policy;

import p5laris.character.domain.domain.enums.GrowthStage;

/**
 * 캐릭터 성장 계산 정책.
 *
 * 경험치 지급 출처가 돌봄/미션으로 늘어나도 레벨과 진행률 계산은 이 정책을 기준으로 통일한다.
 */
public final class CharacterGrowthPolicy {

    public static final int LEVEL_1_EXP = 0;
    public static final int LEVEL_2_EXP = 200;
    public static final int LEVEL_3_EXP = 600;
    public static final int MAX_LEVEL = 3;

    private CharacterGrowthPolicy() {
    }

    public static Growth calculate(int exp) {
        int safeExp = Math.max(0, exp);
        int level = calculateLevel(safeExp);
        GrowthStage growthStage = GrowthStage.fromLevel(level);
        boolean maxLevel = level >= MAX_LEVEL;
        int currentLevelExp = currentLevelExp(level);
        int nextLevelExp = maxLevel ? currentLevelExp : nextLevelExp(level);
        int expToNextLevel = maxLevel ? 0 : Math.max(0, nextLevelExp - safeExp);
        int progressPercent = maxLevel ? 100 : progressPercent(safeExp, currentLevelExp, nextLevelExp);

        return new Growth(
                level,
                safeExp,
                currentLevelExp,
                nextLevelExp,
                expToNextLevel,
                progressPercent,
                growthStage,
                growthStage.label(),
                maxLevel
        );
    }

    public static int calculateLevel(int exp) {
        int safeExp = Math.max(0, exp);
        if (safeExp >= LEVEL_3_EXP) {
            return 3;
        }
        if (safeExp >= LEVEL_2_EXP) {
            return 2;
        }
        return 1;
    }

    private static int currentLevelExp(int level) {
        if (level >= 3) {
            return LEVEL_3_EXP;
        }
        if (level == 2) {
            return LEVEL_2_EXP;
        }
        return LEVEL_1_EXP;
    }

    private static int nextLevelExp(int level) {
        if (level == 1) {
            return LEVEL_2_EXP;
        }
        return LEVEL_3_EXP;
    }

    private static int progressPercent(int exp, int currentLevelExp, int nextLevelExp) {
        int requiredExp = nextLevelExp - currentLevelExp;
        if (requiredExp <= 0) {
            return 100;
        }

        int gainedInLevel = Math.max(0, exp - currentLevelExp);
        int percent = (int) Math.floor((gainedInLevel * 100.0) / requiredExp);
        return Math.max(0, Math.min(99, percent));
    }

    public record Growth(
            int level,
            int exp,
            int currentLevelExp,
            int nextLevelExp,
            int expToNextLevel,
            int progressPercent,
            GrowthStage growthStage,
            String growthStageLabel,
            boolean maxLevel
    ) {
    }
}
