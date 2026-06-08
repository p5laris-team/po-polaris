package p5laris.eventlog.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventLogKafkaConsumerTest {

    @Mock
    private EventLogService eventLogService;

    private EventLogKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventLogKafkaConsumer(eventLogService, new ObjectMapper());
    }

    @Test
    void mission_토픽은_sourceService를_mission으로_저장한다() {
        String payload = """
                {
                  "eventType": "MISSION_COMPLETED",
                  "userId": 1001,
                  "refType": "MISSION",
                  "refId": 2001,
                  "metadata": {"rewardStarPiece": 10},
                  "occurredAt": "2026-05-21T10:15:30+09:00"
                }
                """;

        consumer.consumeEventLog(payload, "mission-event-key-1", "mission-event-logs");

        ArgumentCaptor<EventLogRequest> requestCaptor = ArgumentCaptor.forClass(EventLogRequest.class);
        verify(eventLogService).recordEventLog(requestCaptor.capture());

        EventLogRequest request = requestCaptor.getValue();
        assertThat(request.eventId())
                .isEqualTo(UUID.nameUUIDFromBytes("mission-event-key-1".getBytes(StandardCharsets.UTF_8)));
        assertThat(request.eventType()).isEqualTo("MISSION_COMPLETED");
        assertThat(request.sourceService()).isEqualTo("mission");
        assertThat(request.userId()).isEqualTo(1001L);
        assertThat(request.refType()).isEqualTo("MISSION");
        assertThat(request.refId()).isEqualTo(2001L);
        assertThat(request.propertiesJson()).contains("rewardStarPiece");
        assertThat(request.occurredAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 10, 15, 30));
    }

    @Test
    void ai_토픽은_sourceService를_ai로_저장한다() {
        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventType": "AI_FALLBACK_USED",
                  "userId": 1001,
                  "refType": "AI_MISSION_GENERATION",
                  "refId": 55,
                  "metadata": {"provider": "GEMINI"},
                  "occurredAt": "2026-05-21T10:15:30+09:00"
                }
                """;

        consumer.consumeEventLog(payload, eventId, "ai-event-logs");

        ArgumentCaptor<EventLogRequest> requestCaptor = ArgumentCaptor.forClass(EventLogRequest.class);
        verify(eventLogService).recordEventLog(requestCaptor.capture());

        EventLogRequest request = requestCaptor.getValue();
        assertThat(request.eventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(request.sourceService()).isEqualTo("ai");
        assertThat(request.propertiesJson()).contains("GEMINI");
    }
}
