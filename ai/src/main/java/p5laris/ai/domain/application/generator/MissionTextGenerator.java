package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;

/**
 * 자율 미션 후보 생성 provider를 교체하기 위한 포트다.
 *
 * application service는 rule-based/Gemini/OpenAI 중 어떤 구현을 쓰는지 모르고 이 계약만 호출한다.
 */
public interface MissionTextGenerator {

    MissionTextCandidate generate(MissionTextGenerationCommand command);
}
