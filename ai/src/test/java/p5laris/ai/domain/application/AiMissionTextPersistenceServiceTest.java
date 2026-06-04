package p5laris.ai.domain.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.event.AiEventLogEvent;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.AiUsageLog;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.repository.AiMissionGenerationRepository;
import p5laris.ai.domain.domain.repository.AiUsageLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMissionTextPersistenceServiceTest {

    @Mock
    private AiMissionGenerationRepository aiMissionGenerationRepository;

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SimpleMeterRegistry meterRegistry;

    private AiMissionTextPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        persistenceService = new AiMissionTextPersistenceService(
                aiMissionGenerationRepository,
                aiUsageLogRepository,
                eventPublisher,
                meterRegistry
        );
    }

    @Test
    void fallback_결과를_저장하면_AI_FALLBACK_USED_이벤트를_발행한다() {
        when(aiMissionGenerationRepository.save(any(AiMissionGeneration.class)))
                .thenAnswer(invocation -> {
                    AiMissionGeneration generation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(generation, "id", 55L);
                    return generation;
                });

        AiMissionGeneration savedGeneration = persistenceService.saveGenerationAndUsageLog(
                command(),
                promptTemplate(),
                "request-hash",
                "{\"request\":\"summary\"}",
                "{\"response\":\"summary\"}",
                AiGenerationStatus.FALLBACK,
                AiUsageStatus.FALLBACK,
                true,
                "gemini",
                "gemini-2.5-flash",
                120,
                AiErrorType.PROVIDER_ERROR
        );

        ArgumentCaptor<AiUsageLog> usageCaptor = ArgumentCaptor.forClass(AiUsageLog.class);
        ArgumentCaptor<AiEventLogEvent> eventCaptor = ArgumentCaptor.forClass(AiEventLogEvent.class);

        verify(aiUsageLogRepository).save(usageCaptor.capture());
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(savedGeneration.getId()).isEqualTo(55L);
        assertThat(savedGeneration.isFallbackUsed()).isTrue();
        assertThat(savedGeneration.getStatus()).isEqualTo(AiGenerationStatus.FALLBACK);
        assertThat(savedGeneration.getErrorType()).isEqualTo(AiErrorType.PROVIDER_ERROR);

        AiUsageLog usageLog = usageCaptor.getValue();
        assertThat(usageLog.getStatus()).isEqualTo(AiUsageStatus.FALLBACK);
        assertThat(usageLog.getErrorType()).isEqualTo(AiErrorType.PROVIDER_ERROR);
        assertThat(usageLog.getLatencyMs()).isEqualTo(120);

        AiEventLogEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("AI_FALLBACK_USED");
        assertThat(event.userId()).isEqualTo(1001L);
        assertThat(event.refType()).isEqualTo("AI_MISSION_GENERATION");
        assertThat(event.refId()).isEqualTo(55L);
        assertThat(event.metadata())
                .containsEntry("provider", "GEMINI")
                .containsEntry("model", "gemini-2.5-flash")
                .containsEntry("generationStatus", AiGenerationStatus.FALLBACK)
                .containsEntry("usageStatus", AiUsageStatus.FALLBACK)
                .containsEntry("errorType", AiErrorType.PROVIDER_ERROR)
                .containsEntry("latencyMs", 120);

        // Prometheus Counter 검증
        var counter = meterRegistry.find("ai.generation.requests").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("FALLBACK");
        assertThat(counter.getId().getTag("fallback")).isEqualTo("true");
        assertThat(counter.getId().getTag("error_type")).isEqualTo("PROVIDER_ERROR");
        assertThat(counter.getId().getTag("model")).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void 성공_결과를_저장하면_fallback_이벤트를_발행하지_않는다() {
        when(aiMissionGenerationRepository.save(any(AiMissionGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveGenerationAndUsageLog(
                command(),
                promptTemplate(),
                "request-hash",
                "{\"request\":\"summary\"}",
                "{\"response\":\"summary\"}",
                AiGenerationStatus.SUCCESS,
                AiUsageStatus.SUCCESS,
                false,
                "local",
                "local-tone-v1",
                12,
                null
        );

        verify(aiUsageLogRepository).save(any(AiUsageLog.class));
        verify(eventPublisher, never()).publishEvent(any());

        // Prometheus Counter 검증
        var counter = meterRegistry.find("ai.generation.requests").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("fallback")).isEqualTo("false");
        assertThat(counter.getId().getTag("error_type")).isEqualTo("NONE");
        assertThat(counter.getId().getTag("model")).isEqualTo("local-tone-v1");
    }

    private MissionTextGenerationCommand command() {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                "NOVA",
                3001L,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래?",
                "물 마시고 나서 어땠어?",
                "잘했어.",
                "{}",
                "{}",
                "request-1"
        );
    }

    private PromptTemplate promptTemplate() {
        PromptTemplate promptTemplate = newInstance(PromptTemplate.class);
        ReflectionTestUtils.setField(promptTemplate, "id", 4L);
        ReflectionTestUtils.setField(promptTemplate, "category", PromptCategory.CHARACTER_TONE);
        return promptTemplate;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
