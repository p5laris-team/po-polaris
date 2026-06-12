package p5laris.user.integration;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import p5laris.user.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.user.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnedEvent;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class WalletRewardKafkaBridgeIntegrationTest extends UserIntegrationTestContainers {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private StarPieceTransactionRepository transactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rewardRequest_roundTripsSuccessResultWithOutboxIdentity() throws Exception {
        User user = userRepository.save(User.builder()
                .email("reward-bridge@test.local")
                .nickname("reward-bridge")
                .provider("TEST")
                .role("USER")
                .status("ACTIVE")
                .build());
        walletRepository.save(Wallet.builder().userId(user.getId()).starPiece(10).build());

        String idempotencyKey = "MISSION_REWARD:" + UUID.randomUUID();
        StarPieceEarnRequestedEvent request = rewardRequest(
                700L,
                user.getId(),
                idempotencyKey
        );

        try (Consumer<String, byte[]> consumer = resultConsumer("star-piece-earned")) {
            kafkaTemplate.send("star-piece-earn-requested", idempotencyKey, request);
            kafkaTemplate.flush();

            ConsumerRecord<String, byte[]> record = awaitRecord(
                    consumer,
                    candidate -> idempotencyKey.equals(candidate.key())
            );
            StarPieceEarnedEvent result =
                    objectMapper.readValue(record.value(), StarPieceEarnedEvent.class);

            assertThat(result.getOutboxId()).isEqualTo(700L);
            assertThat(result.getUserId()).isEqualTo(user.getId());
            assertThat(result.getRefId()).isEqualTo(900L);
            assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(result.getBalanceAfter()).isEqualTo(110);
            assertThat(result.getTransactionId()).isNotNull();
            assertThat(transactionRepository.count()).isEqualTo(1);
        }
    }

    @Test
    void rewardRequest_roundTripsFailureResultWithOutboxIdentity() throws Exception {
        String idempotencyKey = "MISSION_REWARD:" + UUID.randomUUID();
        StarPieceEarnRequestedEvent request = rewardRequest(
                701L,
                Long.MAX_VALUE,
                idempotencyKey
        );

        try (Consumer<String, byte[]> consumer = resultConsumer("star-piece-earn-failed")) {
            kafkaTemplate.send("star-piece-earn-requested", idempotencyKey, request);
            kafkaTemplate.flush();

            ConsumerRecord<String, byte[]> record = awaitRecord(
                    consumer,
                    candidate -> idempotencyKey.equals(candidate.key())
            );
            StarPieceEarnFailedEvent result =
                    objectMapper.readValue(record.value(), StarPieceEarnFailedEvent.class);

            assertThat(result.getOutboxId()).isEqualTo(701L);
            assertThat(result.getUserId()).isEqualTo(Long.MAX_VALUE);
            assertThat(result.getRefId()).isEqualTo(900L);
            assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(result.getErrorCode()).isEqualTo("USER-003");
            assertThat(transactionRepository.count()).isZero();
        }
    }

    private StarPieceEarnRequestedEvent rewardRequest(
            Long outboxId,
            Long userId,
            String idempotencyKey
    ) {
        return StarPieceEarnRequestedEvent.builder()
                .outboxId(outboxId)
                .userId(userId)
                .amount(100)
                .reason("MISSION_REWARD")
                .refType("MISSION")
                .refId(900L)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private Consumer<String, byte[]> resultConsumer(String topic) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(),
                "reward-bridge-" + UUID.randomUUID(),
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

    private ConsumerRecord<String, byte[]> awaitRecord(
            Consumer<String, byte[]> consumer,
            Predicate<ConsumerRecord<String, byte[]>> predicate
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, byte[]> record :
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                if (predicate.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka result event was not received within 20 seconds");
    }
}
