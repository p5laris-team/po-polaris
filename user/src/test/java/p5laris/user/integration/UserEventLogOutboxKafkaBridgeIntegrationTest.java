package p5laris.user.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import p5laris.user.domain.application.event.OutboxRelayScheduler;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventLogOutboxKafkaBridgeIntegrationTest extends UserIntegrationTestContainers {

    private static final String TOPIC = "user-event-logs";

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void pendingOutboxEvent_isPublishedOnceAndMarkedSucceeded() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        UserEventLogEvent event = new UserEventLogEvent(
                "PAYMENT_APPROVED",
                1001L,
                "PAYMENT",
                2001L,
                Map.of("amount", 4900, "currency", "KRW"),
                OffsetDateTime.parse("2026-06-15T10:30:00+09:00")
        );
        OutboxEvent outboxEvent = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .aggregateType("USER_EVENT_LOG")
                .aggregateId(event.userId())
                .eventType(event.eventType())
                .payload(objectMapper.writeValueAsString(event))
                .idempotencyKey(idempotencyKey)
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .build());

        try (Consumer<String, byte[]> consumer = createConsumer()) {
            outboxRelayScheduler.relayEvents();

            ConsumerRecord<String, byte[]> record = awaitRecord(consumer, idempotencyKey);
            JsonNode payload = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo(idempotencyKey);
            assertThat(payload.get("eventType").asText()).isEqualTo("PAYMENT_APPROVED");
            assertThat(payload.get("userId").asLong()).isEqualTo(1001L);
            assertThat(payload.get("refType").asText()).isEqualTo("PAYMENT");
            assertThat(payload.get("refId").asLong()).isEqualTo(2001L);
            assertThat(payload.path("metadata").get("amount").asInt()).isEqualTo(4900);

            OutboxEvent relayed = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
            assertThat(relayed.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(relayed.getAttemptCount()).isZero();
            assertThat(relayed.getLastErrorMessage()).isNull();

            outboxRelayScheduler.relayEvents();

            assertThat(countRecordsWithKey(consumer, idempotencyKey, Duration.ofSeconds(2)))
                    .isZero();
        }
    }

    private Consumer<String, byte[]> createConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(),
                "user-outbox-bridge-" + UUID.randomUUID(),
                "false"
        );
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        Consumer<String, byte[]> consumer =
                new DefaultKafkaConsumerFactory<String, byte[]>(properties).createConsumer();
        consumer.subscribe(List.of(TOPIC));
        return consumer;
    }

    private ConsumerRecord<String, byte[]> awaitRecord(
            Consumer<String, byte[]> consumer,
            String idempotencyKey
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, byte[]> record :
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                if (idempotencyKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Outbox Kafka record was not received within 20 seconds");
    }

    private long countRecordsWithKey(
            Consumer<String, byte[]> consumer,
            String idempotencyKey,
            Duration duration
    ) {
        long count = 0;
        for (ConsumerRecord<String, byte[]> record :
                KafkaTestUtils.getRecords(consumer, duration).records(TOPIC)) {
            if (idempotencyKey.equals(record.key())) {
                count++;
            }
        }
        return count;
    }
}
