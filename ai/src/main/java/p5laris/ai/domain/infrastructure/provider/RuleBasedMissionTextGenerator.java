package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.domain.enums.CharacterToneType;
import p5laris.ai.domain.domain.policy.CharacterTonePolicy;

import java.util.Objects;

/**
 * 외부 AI 없이 seed 미션과 캐릭터 타입별 규칙으로 안전한 fallback 후보를 만드는 generator다.
 *
 * provider 장애 또는 운영에서 외부 AI를 끈 상태에서도 API 흐름을 유지하는 기본 구현체다.
 */
@Component
@RequiredArgsConstructor
public class RuleBasedMissionTextGenerator implements MissionTextGenerator {

    private final CharacterTonePolicy characterTonePolicy;

    // 캐릭터 타입을 해석한 뒤 seed title/description에 캐릭터 말투 문구를 더해 fallback 후보를 만든다.
    @Override
    public MissionTextCandidate generate(MissionTextGenerationCommand command) {
        CharacterToneType toneType = characterTonePolicy.resolve(command.characterType());
        long variationSeed = variationSeed(command);

        MissionTextCandidate candidate = new MissionTextCandidate(
                command.baseTitle(),
                command.baseDescription(),
                toneType.characterMessage(command.baseTitle(), variationSeed),
                toneType.completionQuestion(variationSeed),
                toneType.completionResponse(variationSeed),
                command.category(),
                command.difficulty()
        );
        if (toneType != CharacterToneType.MUMU) {
            return candidate;
        }

        return new MissionTextCandidate(
                candidate.title(),
                candidate.description(),
                useCharacterName(candidate.characterMessage(), command.characterName()),
                useCharacterName(candidate.completionQuestion(), command.characterName()),
                useCharacterName(candidate.completionCharacterResponse(), command.characterName()),
                candidate.category(),
                candidate.difficulty()
        );
    }

    private long variationSeed(MissionTextGenerationCommand command) {
        // requestId는 재시도마다 달라질 수 있으므로 제외한다. 같은 미션 문맥이면 같은 말투가 나와야 한다.
        return Objects.hash(
                command.userId(),
                command.characterId(),
                command.characterType(),
                command.missionTemplateId(),
                command.baseTitle()
        );
    }

    private String useCharacterName(String value, String characterName) {
        String name = displayCharacterName(characterName);
        return value.replace("무무가", name + subjectParticle(name));
    }

    private String displayCharacterName(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return "무무";
        }
        return characterName.trim();
    }

    private String subjectParticle(String value) {
        if (value.isBlank()) {
            return "가";
        }
        char last = value.charAt(value.length() - 1);
        if (last < '가' || last > '힣') {
            return "가";
        }
        return (last - '가') % 28 == 0 ? "가" : "이";
    }
}
