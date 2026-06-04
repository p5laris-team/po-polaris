package p5laris.ai.domain.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import com.p5laris.proto.eventlog.v1.RecordEventLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEventLogEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    private AiEventLogEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AiEventLogEventListener();
        ReflectionTestUtils.setField(listener, "sourceService", "ai");
        ReflectionTestUtils.setField(listener, "eventLogStub", eventLogStub);
    }

    @Test
    void AI_이벤트를_event_log_gRPC_요청으로_변환해_전송한다() throws Exception {
        when(eventLogStub.recordEventLog(any(RecordEventLogRequest.class)))
                .thenReturn(RecordEventLogResponse.newBuilder().setRecorded(true).build());

        listener.handle(event());

        ArgumentCaptor<RecordEventLogRequest> requestCaptor = ArgumentCaptor.forClass(RecordEventLogRequest.class);
        verify(eventLogStub).recordEventLog(requestCaptor.capture());

        RecordEventLogRequest request = requestCaptor.getValue();
        assertThat(request.getEventId()).isNotBlank();
        assertThat(request.getEventType()).isEqualTo("AI_FALLBACK_USED");
        assertThat(request.getSourceService()).isEqualTo("ai");
        assertThat(request.getUserId()).isEqualTo(1001L);
        assertThat(request.getRefType()).isEqualTo("AI_MISSION_GENERATION");
        assertThat(request.getRefId()).isEqualTo(55L);
        assertThat(request.hasContextJson()).isFalse();
        assertThat(request.getOccurredAt().getSeconds()).isPositive();

        JsonNode properties = objectMapper.readTree(request.getPropertiesJson());
        assertThat(properties.get("characterId").asLong()).isEqualTo(2001L);
        assertThat(properties.get("requestId").asText()).isEqualTo("request-1");
        assertThat(properties.get("missionTemplateId").asLong()).isEqualTo(3001L);
        assertThat(properties.get("provider").asText()).isEqualTo("GEMINI");
        assertThat(properties.get("model").asText()).isEqualTo("gemini-2.5-flash");
        assertThat(properties.get("generationStatus").asText()).isEqualTo("FALLBACK");
        assertThat(properties.get("usageStatus").asText()).isEqualTo("RATE_LIMITED");
        assertThat(properties.get("errorType").asText()).isEqualTo("RATE_LIMIT_UNAVAILABLE");
        assertThat(properties.get("latencyMs").asInt()).isEqualTo(300);
        assertThat(properties.has("rawPrompt")).isFalse();
        assertThat(properties.has("rawResponse")).isFalse();
        assertThat(properties.has("requestContextJson")).isFalse();
        assertThat(properties.has("responseJson")).isFalse();
    }

    @Test
    void event_log_전송이_실패해도_AI_흐름으로_예외를_전파하지_않는다() {
        when(eventLogStub.recordEventLog(any(RecordEventLogRequest.class)))
                .thenThrow(new RuntimeException("event-log unavailable"));

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
