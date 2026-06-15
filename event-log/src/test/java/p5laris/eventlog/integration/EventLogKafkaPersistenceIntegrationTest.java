package p5laris.eventlog.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import p5laris.eventlog.domain.domain.entity.EventLog;
import p5laris.eventlog.domain.domain.repository.EventLogRepository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EventLogKafkaPersistenceIntegrationTest extends EventLogIntegrationTestContainers {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EventLogRepository eventLogRepository;

    @BeforeEach
    void setUp() {
        eventLogRepository.deleteAll();
    }

    @Test
    void duplicateKafkaKey_isPersistedOnceWithMappedEventFields() {
        String idempotencyKey = "user-event-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "eventType", "PAYMENT_APPROVED",
                "userId", 1001L,
                "refType", "PAYMENT",
                "refId", 2001L,
                "metadata", Map.of("amount", 4900, "currency", "KRW"),
                "occurredAt", "2026-06-15T10:30:00+09:00"
        );

        kafkaTemplate.send("user-event-logs", idempotencyKey, payload);
        kafkaTemplate.send("user-event-logs", idempotencyKey, payload);
        kafkaTemplate.flush();

        UUID expectedEventId = UUID.nameUUIDFromBytes(
                idempotencyKey.getBytes(StandardCharsets.UTF_8)
        );
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(eventLogRepository.count()).isEqualTo(1);
            assertThat(eventLogRepository.existsByEventId(expectedEventId)).isTrue();
        });

        EventLog eventLog = eventLogRepository.findAll().getFirst();
        assertThat(eventLog.getEventId()).isEqualTo(expectedEventId);
        assertThat(eventLog.getEventType()).isEqualTo("PAYMENT_APPROVED");
        assertThat(eventLog.getSourceService()).isEqualTo("user");
        assertThat(eventLog.getUserId()).isEqualTo(1001L);
        assertThat(eventLog.getRefType()).isEqualTo("PAYMENT");
        assertThat(eventLog.getRefId()).isEqualTo(2001L);
        assertThat(eventLog.getPropertiesJson().get("amount").asInt()).isEqualTo(4900);
        assertThat(eventLog.getPropertiesJson().get("currency").asText()).isEqualTo("KRW");
    }
}
