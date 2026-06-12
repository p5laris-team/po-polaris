package p5laris.user.domain.application;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTxHelperTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    private PaymentTxHelper paymentTxHelper;

    @BeforeEach
    void setUp() {
        paymentTxHelper = new PaymentTxHelper(orderRepository, transactionRepository, walletService);
    }

    @Test
    @DisplayName("결제 승인 쓰기는 주문을 잠근 뒤 주문 완료, 거래 기록, 지갑 충전을 하나의 경계로 수행한다")
    void writePaymentApproval_readyOrder_persistsTransactionAndChargesWallet() {
        PaymentOrder order = readyOrder();
        when(orderRepository.findByOrderNoForUpdate("order-100")).thenReturn(Optional.of(order));
        when(walletService.getMyWallet(1L))
                .thenReturn(Wallet.builder()
                        .userId(1L)
                        .starPiece(700)
                        .build());

        int balance = paymentTxHelper.writePaymentApproval(
                1L,
                "order-100",
                "payment-100",
                "TOSS_PAYMENTS",
                "CARD"
        );

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        assertThat(balance).isEqualTo(700);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository).save(order);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getPaymentOrderId()).isEqualTo(10L);
        assertThat(transactionCaptor.getValue().getPaymentId()).isEqualTo("payment-100");
        verify(walletService).earnStarPiece(
                1L,
                200,
                "PAYMENT_CHARGE",
                "PAYMENT_ORDER",
                10L,
                "PAYMENT:order-100"
        );
    }

    @Test
    @DisplayName("이미 PAID인 주문을 다시 승인하면 payment transaction과 지갑 충전을 추가하지 않는다")
    void writePaymentApproval_paidOrder_returnsWalletWithoutDuplicateWrites() {
        PaymentOrder order = PaymentOrder.builder()
                .id(10L)
                .userId(1L)
                .orderNo("order-100")
                .amount(1000)
                .starPieces(200)
                .status(PaymentStatus.PAID)
                .build();
        when(orderRepository.findByOrderNoForUpdate("order-100")).thenReturn(Optional.of(order));
        when(walletService.getMyWallet(1L))
                .thenReturn(Wallet.builder()
                        .userId(1L)
                        .starPiece(700)
                        .build());

        int balance = paymentTxHelper.writePaymentApproval(
                1L,
                "order-100",
                "payment-100",
                "TOSS_PAYMENTS",
                "CARD"
        );

        assertThat(balance).isEqualTo(700);
        verify(orderRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).earnStarPiece(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("검증 실패 기록은 READY 주문만 FAILED로 바꾸고 이미 확정된 주문은 덮어쓰지 않는다")
    void writePaymentFailure_skipsAlreadyPaidOrder() {
        PaymentOrder order = PaymentOrder.builder()
                .id(10L)
                .userId(1L)
                .orderNo("order-100")
                .amount(1000)
                .starPieces(200)
                .status(PaymentStatus.PAID)
                .build();
        when(orderRepository.findByOrderNoForUpdate("order-100")).thenReturn(Optional.of(order));

        paymentTxHelper.writePaymentFailure("order-100", PaymentStatus.FAILED);

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("READY 주문의 검증 실패는 FAILED로 저장한다")
    void writePaymentFailure_readyOrder_marksFailed() {
        PaymentOrder order = readyOrder();
        when(orderRepository.findByOrderNoForUpdate("order-100")).thenReturn(Optional.of(order));

        paymentTxHelper.writePaymentFailure("order-100", PaymentStatus.FAILED);

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository).save(order);
    }

    private PaymentOrder readyOrder() {
        return PaymentOrder.builder()
                .id(10L)
                .userId(1L)
                .orderNo("order-100")
                .amount(1000)
                .starPieces(200)
                .status(PaymentStatus.READY)
                .build();
    }
}
