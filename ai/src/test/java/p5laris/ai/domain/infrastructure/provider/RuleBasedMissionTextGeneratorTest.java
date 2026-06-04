package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.domain.policy.CharacterTonePolicy;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedMissionTextGeneratorTest {

    private final RuleBasedMissionTextGenerator generator = new RuleBasedMissionTextGenerator(new CharacterTonePolicy());

    @Test
    void 무무_문구는_같은_미션_문맥에서_항상_같게_생성된다() {
        MissionTextGenerationCommand command = validCommand(3001L, UUID.randomUUID().toString());

        MissionTextCandidate first = generator.generate(command);
        MissionTextCandidate second = generator.generate(validCommand(3001L, UUID.randomUUID().toString()));

        assertThat(second.characterMessage()).isEqualTo(first.characterMessage());
        assertThat(second.completionQuestion()).isEqualTo(first.completionQuestion());
        assertThat(second.completionCharacterResponse()).isEqualTo(first.completionCharacterResponse());
    }

    @Test
    void 무무_문구는_미션_문맥에_따라_여러_패턴으로_분산된다() {
        Set<String> characterMessages = LongStream.rangeClosed(3001L, 3012L)
                .mapToObj(templateId -> generator.generate(validCommand(templateId, UUID.randomUUID().toString())))
                .map(MissionTextCandidate::characterMessage)
                .collect(Collectors.toSet());

        assertThat(characterMessages).hasSizeGreaterThan(1);
        assertThat(characterMessages).allSatisfy(this::assertMumuText);
    }

    @Test
    void 쪼리_문구도_미션_문맥에_따라_안전한_밈_말투로_분산된다() {
        Set<String> characterMessages = LongStream.rangeClosed(3001L, 3012L)
                .mapToObj(templateId -> generator.generate(validCommand("JJORY", templateId, UUID.randomUUID().toString())))
                .map(MissionTextCandidate::characterMessage)
                .collect(Collectors.toSet());

        assertThat(characterMessages).hasSizeGreaterThan(1);
        assertThat(characterMessages)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("욕", "한심", "왜 못")
                        .matches(".*(가보자고|인정|작전|선방|가능|나쁘지 않음).*"));
    }

    private void assertMumuText(String text) {
        assertThat(text)
                .startsWith("무")
                .contains("(해석:")
                .contains("무무가")
                .endsWith(")");
    }

    private MissionTextGenerationCommand validCommand(Long missionTemplateId, String requestId) {
        return validCommand("MUMU", missionTemplateId, requestId);
    }

    private MissionTextGenerationCommand validCommand(String characterType, Long missionTemplateId, String requestId) {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                characterType,
                missionTemplateId,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
                "물 마시고 나서 기분이 조금 달라졌어?",
                "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
                "{}",
                "{}",
                requestId
        );
    }
}
