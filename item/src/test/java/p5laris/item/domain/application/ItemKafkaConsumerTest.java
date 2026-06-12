package p5laris.item.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.item.domain.application.event.StarPieceSpentEvent;
import p5laris.item.domain.application.event.StarPieceSpendFailedEvent;
import p5laris.item.domain.domain.entity.Item;
import p5laris.item.domain.domain.entity.UserItem;
import p5laris.item.domain.domain.entity.UserItemPurchase;
import p5laris.item.domain.domain.repository.UserItemPurchaseRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemKafkaConsumerTest {

    @Mock
    private UserItemPurchaseRepository userItemPurchaseRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper;
    private ItemKafkaConsumer itemKafkaConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        itemKafkaConsumer = new ItemKafkaConsumer(
                userItemPurchaseRepository,
                userItemRepository,
                eventPublisher,
                transactionTemplate,
                objectMapper
        );

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("SYSTEM_ERROR 실패 이벤트는 구매를 UNKNOWN으로 돌려 Saga 복구 대상에 남긴다")
    void handleStarPieceSpendFailed_systemError_marksUnknown() throws Exception {
        UserItemPurchase purchase = pendingPurchase(10L);
        when(userItemPurchaseRepository.findById(10L)).thenReturn(Optional.of(purchase));

        StarPieceSpendFailedEvent event = StarPieceSpendFailedEvent.builder()
                .purchaseId(10L)
                .errorCode("SYSTEM_ERROR")
                .build();

        LocalDateTime before = LocalDateTime.now();
        itemKafkaConsumer.handleStarPieceSpendFailed(objectMapper.writeValueAsString(event));
        LocalDateTime after = LocalDateTime.now();

        assertThat(purchase.getStatus()).isEqualTo("UNKNOWN");
        assertThat(purchase.getAttemptCount()).isEqualTo(1);
        assertThat(purchase.getNextAttemptAt())
                .isBetween(before.plusSeconds(50), after.plusSeconds(70));
        verify(userItemPurchaseRepository).save(purchase);
    }

    @Test
    @DisplayName("비즈니스 실패 이벤트는 구매를 FAILED로 확정한다")
    void handleStarPieceSpendFailed_businessError_marksFailed() throws Exception {
        UserItemPurchase purchase = pendingPurchase(20L);
        when(userItemPurchaseRepository.findById(20L)).thenReturn(Optional.of(purchase));

        StarPieceSpendFailedEvent event = StarPieceSpendFailedEvent.builder()
                .purchaseId(20L)
                .errorCode("STAR_PIECE_NOT_ENOUGH")
                .build();

        itemKafkaConsumer.handleStarPieceSpendFailed(objectMapper.writeValueAsString(event));

        assertThat(purchase.getStatus()).isEqualTo("FAILED");
        assertThat(purchase.getAttemptCount()).isZero();
        verify(userItemPurchaseRepository).save(purchase);
    }

    @Test
    @DisplayName("차감 성공 이벤트가 중복 delivery되어도 구매 확정과 아이템 지급은 한 번만 수행한다")
    void handleStarPieceSpent_duplicateDelivery_completesPurchaseOnlyOnce() throws Exception {
        UserItem userItem = UserItem.builder()
                .id(100L)
                .userId(1L)
                .item(Item.builder()
                        .id(200L)
                        .name("테스트 아이템")
                        .itemType("FOOD")
                        .price(100)
                        .build())
                .quantity(1)
                .build();
        UserItemPurchase purchase = UserItemPurchase.builder()
                .id(30L)
                .userId(1L)
                .userItem(userItem)
                .itemId(200L)
                .quantity(1)
                .price(100)
                .starPiece(0)
                .transactionId(0L)
                .idempotencyKey("ITEM_PURCHASE:30")
                .status("PENDING")
                .build();
        when(userItemPurchaseRepository.findById(30L)).thenReturn(Optional.of(purchase));

        StarPieceSpentEvent event = StarPieceSpentEvent.builder()
                .purchaseId(30L)
                .remainingStarPiece(400)
                .transactionId(950L)
                .build();
        String payload = objectMapper.writeValueAsString(event);

        itemKafkaConsumer.handleStarPieceSpent(payload);
        itemKafkaConsumer.handleStarPieceSpent(payload);

        assertThat(purchase.getStatus()).isEqualTo("COMPLETED");
        assertThat(purchase.getTransactionId()).isEqualTo(950L);
        assertThat(userItem.getQuantity()).isEqualTo(2);
        verify(userItemPurchaseRepository, times(1)).save(purchase);
        verify(userItemRepository, times(1)).save(userItem);
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    private UserItemPurchase pendingPurchase(Long purchaseId) {
        return UserItemPurchase.builder()
                .id(purchaseId)
                .userId(1L)
                .itemId(100L)
                .quantity(1)
                .price(100)
                .starPiece(0)
                .transactionId(0L)
                .idempotencyKey("ITEM_PURCHASE:" + purchaseId)
                .status("PENDING")
                .build();
    }
}
