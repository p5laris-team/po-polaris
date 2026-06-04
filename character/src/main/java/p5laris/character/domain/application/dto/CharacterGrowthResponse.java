package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * 캐릭터 성장 UI에 필요한 계산 결과.
 */
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
