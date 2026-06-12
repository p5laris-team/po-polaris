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
import p5laris.user.domain.application.event.ItemPurchaseRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.user.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnedEvent;
import p5laris.user.domain.application.event.StarPieceSpentEvent;
import p5laris.user.domain.application.event.StarPieceSpendFailedEvent;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.exception.KafkaConsumerProcessingException;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("별조각 적립 요청이 중복 delivery되어도 같은 멱등키와 거래 ID로 성공 이벤트를 재발행한다")
    void handleStarPieceEarnRequested_duplicateDelivery_reusesIdempotentTransaction() throws Exception {
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

        String payload = objectMapper.writeValueAsString(request);
        userKafkaConsumer.handleStarPieceEarnRequested(payload);
        userKafkaConsumer.handleStarPieceEarnRequested(payload);

        ArgumentCaptor<StarPieceEarnedEvent> eventCaptor = ArgumentCaptor.forClass(StarPieceEarnedEvent.class);
        verify(walletService, times(2))
                .earnStarPiece(1L, 15, "MISSION_REWARD", "MISSION", 100L, "MISSION_REWARD:100");
        verify(kafkaTemplate, times(2))
                .send(eq("star-piece-earned"), eq("MISSION_REWARD:100"), eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(StarPieceEarnedEvent::getTransactionId)
                .containsExactly(900L, 900L);
        assertThat(eventCaptor.getAllValues())
                .extracting(StarPieceEarnedEvent::getIdempotencyKey)
                .containsExactly("MISSION_REWARD:100", "MISSION_REWARD:100");
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

    @Test
    @DisplayName("아이템 구매 차감 중 시스템 예외가 발생하면 실패 이벤트로 확정하지 않고 예외를 전파한다")
    void handleItemPurchaseRequest_systemException_rethrows() throws Exception {
        ItemPurchaseRequestedEvent request = ItemPurchaseRequestedEvent.builder()
                .purchaseId(30L)
                .userId(3L)
                .itemId(300L)
                .price(100)
                .idempotencyKey("ITEM_PURCHASE:30")
                .build();
        when(walletService.spendStarPiece(3L, 100, "ITEM_PURCHASE", "ITEM", 300L, "ITEM_PURCHASE:30"))
                .thenThrow(new RuntimeException("database timeout"));

        assertThatThrownBy(() -> userKafkaConsumer.handleItemPurchaseRequest(objectMapper.writeValueAsString(request)))
                .isInstanceOf(KafkaConsumerProcessingException.class)
                .hasMessageContaining("아이템 구매 요청 처리에 실패했습니다.");

        verify(kafkaTemplate, never()).send(eq("star-piece-spend-failed"), anyString(), any());
    }

    @Test
    @DisplayName("아이템 구매 차감 중 잔액 부족은 구매 실패 이벤트를 발행한다")
    void handleItemPurchaseRequest_notEnoughStarPiece_publishesFailedEvent() throws Exception {
        ItemPurchaseRequestedEvent request = ItemPurchaseRequestedEvent.builder()
                .purchaseId(40L)
                .userId(4L)
                .itemId(400L)
                .price(100)
                .idempotencyKey("ITEM_PURCHASE:40")
                .build();
        when(walletService.spendStarPiece(4L, 100, "ITEM_PURCHASE", "ITEM", 400L, "ITEM_PURCHASE:40"))
                .thenThrow(new UserException(UserErrorCode.STAR_PIECE_NOT_ENOUGH));

        userKafkaConsumer.handleItemPurchaseRequest(objectMapper.writeValueAsString(request));

        ArgumentCaptor<StarPieceSpendFailedEvent> eventCaptor = ArgumentCaptor.forClass(StarPieceSpendFailedEvent.class);
        verify(kafkaTemplate).send(eq("star-piece-spend-failed"), eq("ITEM_PURCHASE:40"), eventCaptor.capture());

        StarPieceSpendFailedEvent event = eventCaptor.getValue();
        assertThat(event.getPurchaseId()).isEqualTo(40L);
        assertThat(event.getErrorCode()).isEqualTo("STAR_PIECE_NOT_ENOUGH");
    }

    @Test
    @DisplayName("아이템 구매 요청이 중복 delivery되어도 같은 멱등키와 거래 ID로 차감 성공 이벤트를 재발행한다")
    void handleItemPurchaseRequest_duplicateDelivery_reusesIdempotentTransaction() throws Exception {
        ItemPurchaseRequestedEvent request = ItemPurchaseRequestedEvent.builder()
                .purchaseId(50L)
                .userId(5L)
                .itemId(500L)
                .price(100)
                .idempotencyKey("ITEM_PURCHASE:50")
                .build();
        StarPieceTransaction transaction = StarPieceTransaction.builder()
                .id(950L)
                .userId(5L)
                .transactionType("SPEND")
                .amount(-100)
                .balanceAfter(400)
                .reason("ITEM_PURCHASE")
                .refType("ITEM")
                .refId(500L)
                .idempotencyKey("ITEM_PURCHASE:50")
                .build();
        when(walletService.spendStarPiece(5L, 100, "ITEM_PURCHASE", "ITEM", 500L, "ITEM_PURCHASE:50"))
                .thenReturn(transaction);

        String payload = objectMapper.writeValueAsString(request);
        userKafkaConsumer.handleItemPurchaseRequest(payload);
        userKafkaConsumer.handleItemPurchaseRequest(payload);

        ArgumentCaptor<StarPieceSpentEvent> eventCaptor = ArgumentCaptor.forClass(StarPieceSpentEvent.class);
        verify(walletService, times(2))
                .spendStarPiece(5L, 100, "ITEM_PURCHASE", "ITEM", 500L, "ITEM_PURCHASE:50");
        verify(kafkaTemplate, times(2))
                .send(eq("star-piece-spent"), eq("ITEM_PURCHASE:50"), eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(StarPieceSpentEvent::getTransactionId)
                .containsExactly(950L, 950L);
        assertThat(eventCaptor.getAllValues())
                .extracting(StarPieceSpentEvent::getRemainingStarPiece)
                .containsExactly(400, 400);
    }
}
