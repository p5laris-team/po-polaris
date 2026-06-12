package p5laris.ai.domain.application.event;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.enums.PromptCategory;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiEventLogEventTest {

    @Test
    void ai_fallback_이벤트는_prompt와_response_전문_없이_요약값만_담는다() throws Exception {
        MissionTextGenerationCommand command = command();
        PromptTemplate promptTemplate = promptTemplate();
        AiMissionGeneration generation = generation();

        AiEventLogEvent event = AiEventLogEvent.fallbackUsed(
                command,
                promptTemplate,
                generation,
                AiGenerationStatus.FALLBACK,
                AiUsageStatus.FALLBACK,
                "local",
                "local-tone-v1",
                42,
                AiErrorType.POLICY_VIOLATION
        );

        assertThat(event.eventType()).isEqualTo("AI_FALLBACK_USED");
        assertThat(event.userId()).isEqualTo(1001L);
        assertThat(event.refType()).isEqualTo("AI_MISSION_GENERATION");
        assertThat(event.refId()).isEqualTo(55L);

        Map<String, Object> metadata = event.metadata();
        assertThat(metadata)
                .containsEntry("requestId", "request-1")
                .containsEntry("characterId", 2001L)
                .containsEntry("missionTemplateId", 3001L)
                .containsEntry("promptTemplateId", 4L)
                .containsEntry("promptCategory", PromptCategory.CHARACTER_TONE)
                .containsEntry("provider", "LOCAL")
                .containsEntry("model", "local-tone-v1")
                .containsEntry("generationStatus", AiGenerationStatus.FALLBACK)
                .containsEntry("usageStatus", AiUsageStatus.FALLBACK)
                .containsEntry("errorType", AiErrorType.POLICY_VIOLATION)
                .containsEntry("latencyMs", 42);
        assertThat(metadata).doesNotContainKeys("rawPrompt", "rawResponse", "requestContextJson", "responseJson");
    }

    private MissionTextGenerationCommand command() {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                "NOVA",
                "노바",
                3001L,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래?",
                "물 마시고 나서 어땠어?",
                "잘했어.",
                "{\"routineGoal\":\"WAKE_UP\"}",
                "{\"recentRejected\":[]}",
                "request-1"
        );
    }

    private PromptTemplate promptTemplate() throws Exception {
        PromptTemplate promptTemplate = newInstance(PromptTemplate.class);
        ReflectionTestUtils.setField(promptTemplate, "id", 4L);
        ReflectionTestUtils.setField(promptTemplate, "category", PromptCategory.CHARACTER_TONE);
        return promptTemplate;
    }

    private AiMissionGeneration generation() throws Exception {
        AiMissionGeneration generation = newInstance(AiMissionGeneration.class);
        ReflectionTestUtils.setField(generation, "id", 55L);
        return generation;
    }

    private <T> T newInstance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
