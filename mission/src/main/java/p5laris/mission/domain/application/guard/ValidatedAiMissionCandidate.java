package p5laris.mission.domain.application.guard;

import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;

/**
 * mission 서버 후검증을 통과해 저장 가능한 AI 미션 후보다.
 */
public record ValidatedAiMissionCandidate(
        Long aiGenerationId,
        String title,
        String description,
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse,
        MissionCategoryType category,
        MissionDifficultyType difficulty
) {
}
