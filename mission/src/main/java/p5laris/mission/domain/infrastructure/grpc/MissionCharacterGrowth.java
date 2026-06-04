package p5laris.mission.domain.infrastructure.grpc;

public record MissionCharacterGrowth(
        int level,
        int exp,
        int currentLevelExp,
        int nextLevelExp,
        int expToNextLevel,
        int progressPercent,
        String growthStage,
        String growthStageLabel,
        boolean maxLevel
) {
}
