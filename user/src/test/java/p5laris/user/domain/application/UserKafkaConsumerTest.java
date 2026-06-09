package p5laris.user.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import p5laris.user.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.user.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnedEvent;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserKafkaConsumerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ObjectMapper objectMapper;
    private UserKafkaConsumer userKafkaConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userKafkaConsumer = new UserKafkaConsumer(walletService, kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("별조각 적립 요청을 처리하고 성공 이벤트를 발행한다")
    void handleStarPieceEarnRequested_success_publishesEarnedEvent() throws Exception {
        StarPieceEarnRequestedEvent request = StarPieceEarnRequestedEvent.builder()
                .outboxId(10L)
                .userId(1L)
                .amount(15)
                .reason("MISSION_REWARD")
                .refType("MISSION")
                .refId(100L)
                .idempotencyKey("MISSION_REWARD:100")
                .build();
        StarPieceTransaction transaction = StarPieceTransaction.builder()
                .id(900L)
                .userId(1L)
                .amount(15)
                .balanceAfter(115)
                .reason("MISSION_REWARD")
                .refType("MISSION")
                .refId(100L)
                .idempotencyKey("MISSION_REWARD:100")
                .build();
        when(walletService.earnStarPiece(1L, 15, "MISSION_REWARD", "MISSION", 100L, "MISSION_REWARD:100"))
                .thenReturn(transaction);

        userKafkaConsumer.handleStarPieceEarnRequested(objectMapper.writeValueAsString(request));

        ArgumentCaptor<StarPieceEarnedEvent> eventCaptor = ArgumentCaptor.forClass(StarPieceEarnedEvent.class);
        verify(kafkaTemplate).send(eq("star-piece-earned"), eq("MISSION_REWARD:100"), eventCaptor.capture());

        StarPieceEarnedEvent event = eventCaptor.getValue();
        assertThat(event.getOutboxId()).isEqualTo(10L);
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getAmount()).isEqualTo(15);
        assertThat(event.getReason()).isEqualTo("MISSION_REWARD");
        assertThat(event.getRefType()).isEqualTo("MISSION");
        assertThat(event.getRefId()).isEqualTo(100L);
        assertThat(event.getIdempotencyKey()).isEqualTo("MISSION_REWARD:100");
        assertThat(event.getBalanceAfter()).isEqualTo(115);
        assertThat(event.getTransactionId()).isEqualTo(900L);
    }

    @Test
    @DisplayName("별조각 적립 실패 시 요청 모듈이 처리할 실패 이벤트를 발행한다")
    void handleStarPieceEarnRequested_userException_publishesFailedEvent() throws Exception {
        StarPieceEarnRequestedEvent request = StarPieceEarnRequestedEvent.builder()
                .outboxId(20L)
                .userId(2L)
                .amount(10)
                .reason("SHARE_REWARD")
                .refType("SHARE")
                .refId(200L)
                .idempotencyKey("SHARE_REWARD:2:2026-06-09")
                .build();
        when(walletService.earnStarPiece(2L, 10, "SHARE_REWARD", "SHARE", 200L, "SHARE_REWARD:2:2026-06-09"))
                .thenThrow(new UserException(UserErrorCode.WALLET_NOT_FOUND));

        userKafkaConsumer.handleStarPieceEarnRequested(objectMapper.writeValueAsString(request));

        ArgumentCaptor<StarPieceEarnFailedEvent> eventCaptor = ArgumentCaptor.forClass(StarPieceEarnFailedEvent.class);
        verify(kafkaTemplate).send(eq("star-piece-earn-failed"), eq("SHARE_REWARD:2:2026-06-09"), eventCaptor.capture());

        StarPieceEarnFailedEvent event = eventCaptor.getValue();
        assertThat(event.getOutboxId()).isEqualTo(20L);
        assertThat(event.getReason()).isEqualTo("SHARE_REWARD");
        assertThat(event.getRefType()).isEqualTo("SHARE");
        assertThat(event.getRefId()).isEqualTo(200L);
        assertThat(event.getErrorCode()).isEqualTo("USER-003");
    }
}
