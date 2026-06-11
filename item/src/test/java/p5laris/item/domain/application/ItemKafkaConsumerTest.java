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
import p5laris.item.domain.application.event.StarPieceSpendFailedEvent;
import p5laris.item.domain.domain.entity.UserItemPurchase;
import p5laris.item.domain.domain.repository.UserItemPurchaseRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
