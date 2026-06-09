package p5laris.ai.domain.application.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEventLogEventListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AiEventLogEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AiEventLogEventListener(kafkaTemplate);
    }

    @Test
    void AI_이벤트를_Kafka_토픽으로_발행한다() {
        AiEventLogEvent event = event();

        listener.handle(event);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("ai-event-logs"), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isSameAs(event);
    }

    @Test
    void Kafka_발행이_실패해도_AI_흐름으로_예외를_전파하지_않는다() {
        when(kafkaTemplate.send(eq("ai-event-logs"), anyString(), any()))
                .thenThrow(new RuntimeException("kafka unavailable"));

        assertThatCode(() -> listener.handle(event()))
                .doesNotThrowAnyException();
    }

    private AiEventLogEvent event() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", "request-1");
        metadata.put("characterId", 2001L);
        metadata.put("missionTemplateId", 3001L);
        metadata.put("promptTemplateId", 4L);
        metadata.put("promptCategory", "CHARACTER_TONE");
        metadata.put("provider", "GEMINI");
        metadata.put("model", "gemini-2.5-flash");
        metadata.put("generationStatus", AiGenerationStatus.FALLBACK);
        metadata.put("usageStatus", AiUsageStatus.RATE_LIMITED);
        metadata.put("errorType", AiErrorType.RATE_LIMIT_UNAVAILABLE);
        metadata.put("latencyMs", 300);

        return new AiEventLogEvent(
                "AI_FALLBACK_USED",
                1001L,
                "AI_MISSION_GENERATION",
                55L,
                metadata,
                OffsetDateTime.parse("2026-05-21T10:15:30+09:00")
        );
    }
}
