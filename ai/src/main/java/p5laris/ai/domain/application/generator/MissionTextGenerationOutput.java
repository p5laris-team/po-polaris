package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextCandidate;

/**
 * 미션 문구 생성 결과와 provider가 내려준 실제 token usage metadata를 함께 전달한다.
 */
public record MissionTextGenerationOutput(
        MissionTextCandidate candidate,
        AiTokenUsage tokenUsage
) {

    public MissionTextGenerationOutput {
        tokenUsage = tokenUsage != null ? tokenUsage : AiTokenUsage.empty();
    }

    public static MissionTextGenerationOutput withoutUsage(MissionTextCandidate candidate) {
        return new MissionTextGenerationOutput(candidate, AiTokenUsage.empty());
    }
}
