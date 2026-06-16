package p5laris.user.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerRetryIntegrationTest extends UserIntegrationTestContainers {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("처리할 수 없는 Kafka 이벤트는 재시도 후 DLT로 이동한다")
    void invalidEvent_isPublishedToDeadLetterTopicAfterRetries() {
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(),
                "dlt-test-" + UUID.randomUUID(),
                "false"
        );

        consumerProperties.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class
        );
        consumerProperties.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class
        );

        try (Consumer<String, byte[]> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, byte[]>(consumerProperties)
                             .createConsumer()) {
            dltConsumer.subscribe(java.util.List.of("star-piece-earn-requested.DLT"));

            kafkaTemplate.send(
                    "star-piece-earn-requested",
                    "invalid-event",
                    "not-a-valid-reward-event"
            );
            kafkaTemplate.flush();

            ConsumerRecord<String, byte[]> record = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    "star-piece-earn-requested.DLT",
                    Duration.ofSeconds(15)
            );

            assertThat(record.key()).isEqualTo("invalid-event");
            assertThat(new String(record.value(), StandardCharsets.UTF_8))
                    .contains("not-a-valid-reward-event");
        }
    }
}
