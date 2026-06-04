package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

@Builder
public record CharacterGrowthResponse(
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
