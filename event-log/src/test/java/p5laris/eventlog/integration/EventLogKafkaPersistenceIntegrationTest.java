package p5laris.eventlog.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import p5laris.eventlog.domain.domain.entity.EventLog;
import p5laris.eventlog.domain.domain.repository.EventLogRepository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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

    @Test
    void invalidPayload_isPublishedToDeadLetterTopicWithoutPersisting() {
        String idempotencyKey = "invalid-user-event-" + UUID.randomUUID();

        try (Consumer<String, byte[]> consumer = createConsumer("user-event-logs.DLT")) {
            kafkaTemplate.send("user-event-logs", idempotencyKey, "not-a-valid-event-log");
            kafkaTemplate.flush();

            ConsumerRecord<String, byte[]> record = KafkaTestUtils.getSingleRecord(
                    consumer,
                    "user-event-logs.DLT",
                    Duration.ofSeconds(15)
            );

            assertThat(record.key()).isEqualTo(idempotencyKey);
            assertThat(new String(record.value(), StandardCharsets.UTF_8))
                    .contains("not-a-valid-event-log");
            assertThat(eventLogRepository.count()).isZero();
        }
    }

    private Consumer<String, byte[]> createConsumer(String topic) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(),
                "event-log-dlt-" + UUID.randomUUID(),
                "false"
        );
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        Consumer<String, byte[]> consumer =
                new DefaultKafkaConsumerFactory<String, byte[]>(properties).createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
