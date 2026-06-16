package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.domain.enums.AiProviderType;

/**
 * Gemini/OpenAI처럼 외부 provider를 호출하는 generator 계약이다.
 *
 * rule-based generator와 분리해두면 provider가 늘어나도 application service는 그대로 유지할 수 있다.
 */
public interface ExternalMissionTextGenerator {

    AiProviderType providerType();

    MissionTextCandidate generate(MissionTextGenerationCommand command);

    default MissionTextGenerationOutput generateWithUsage(MissionTextGenerationCommand command) {
        return MissionTextGenerationOutput.withoutUsage(generate(command));
    }
}
