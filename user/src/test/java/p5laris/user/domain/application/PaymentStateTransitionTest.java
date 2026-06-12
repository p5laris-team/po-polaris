package p5laris.user.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.PaymentTransaction;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStateTransitionTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Test
    @DisplayName("READY 주문만 PAID로 전이하며 거래와 PAYMENT 멱등키를 기록한다")
    void writePaymentApproval_readyOrder_transitionsToPaid() {
        PaymentOrder readyOrder = order(10L, 1L, "order-1", PaymentStatus.READY);
        PaymentTxHelper helper = helper();
        when(orderRepository.findByOrderNo("order-1")).thenReturn(Optional.of(readyOrder));
        when(walletService.getMyWallet(1L))
                .thenReturn(Wallet.builder().userId(1L).starPiece(350).build());

        int balance = helper.writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        );

        assertThat(balance).isEqualTo(350);
        assertThat(readyOrder.getStatus()).isEqualTo(PaymentStatus.PAID);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getPaymentOrderId()).isEqualTo(10L);
        assertThat(transactionCaptor.getValue().getPaymentId()).isEqualTo("payment-1");

        verify(walletService).earnStarPiece(
                1L,
                100,
                "PAYMENT_CHARGE",
                "PAYMENT_ORDER",
                10L,
                "PAYMENT:order-1"
        );
    }

    @Test
    @DisplayName("FAILED 주문은 PAID로 전이할 수 없다")
    void writePaymentApproval_failedOrder_rejectsTransition() {
        PaymentOrder failedOrder = order(10L, 1L, "order-1", PaymentStatus.FAILED);
        PaymentTxHelper helper = helper();
        when(orderRepository.findByOrderNo("order-1")).thenReturn(Optional.of(failedOrder));

        assertThatThrownBy(() -> helper.writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.PAYMENT_ALREADY_PROCESSED);

        assertThat(failedOrder.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).earnStarPiece(
                any(), any(Integer.class), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("다른 사용자의 주문은 존재하지 않는 주문처럼 거부한다")
    void writePaymentApproval_otherOwner_rejectsTransition() {
        PaymentOrder anotherUsersOrder = order(10L, 2L, "order-1", PaymentStatus.READY);
        PaymentTxHelper helper = helper();
        when(orderRepository.findByOrderNo("order-1")).thenReturn(Optional.of(anotherUsersOrder));

        assertThatThrownBy(() -> helper.writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.PAYMENT_ORDER_NOT_FOUND);

        assertThat(anotherUsersOrder.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(orderRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    private PaymentTxHelper helper() {
        return new PaymentTxHelper(orderRepository, transactionRepository, walletService);
    }

    private PaymentOrder order(Long id, Long userId, String orderNo, PaymentStatus status) {
        return PaymentOrder.builder()
                .id(id)
                .userId(userId)
                .orderNo(orderNo)
                .amount(1_000)
                .starPieces(100)
                .status(status)
                .build();
    }
}
