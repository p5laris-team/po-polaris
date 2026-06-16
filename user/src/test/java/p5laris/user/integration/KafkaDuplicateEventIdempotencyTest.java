package p5laris.user.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import p5laris.user.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KafkaDuplicateEventIdempotencyTest extends UserIntegrationTestContainers {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private StarPieceTransactionRepository transactionRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .email("kafka-idempotency@test.local")
                .nickname("kafka-test")
                .provider("TEST")
                .role("USER")
                .status("ACTIVE")
                .build());
        userId = user.getId();
        walletRepository.save(Wallet.builder().userId(userId).starPiece(10).build());
    }

    @Test
    @DisplayName("같은 Kafka 보상 이벤트를 두 번 보내도 지갑은 한 번만 증가한다")
    void duplicateRewardEvent_isAppliedOnce() {
        String idempotencyKey = "MISSION_REWARD:900";
        StarPieceEarnRequestedEvent event = StarPieceEarnRequestedEvent.builder()
                .outboxId(700L)
                .userId(userId)
                .amount(100)
                .reason("MISSION_REWARD")
                .refType("MISSION")
                .refId(900L)
                .idempotencyKey(idempotencyKey)
                .build();

        kafkaTemplate.send("star-piece-earn-requested", idempotencyKey, event);
        kafkaTemplate.send("star-piece-earn-requested", idempotencyKey, event);
        kafkaTemplate.flush();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(walletRepository.findByUserId(userId).orElseThrow().getStarPiece())
                    .isEqualTo(110);
        });
    }
}
