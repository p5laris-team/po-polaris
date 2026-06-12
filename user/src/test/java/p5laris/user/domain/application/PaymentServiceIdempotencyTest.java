package p5laris.user.domain.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceIdempotencyTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PaymentTxHelper paymentTxHelper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                orderRepository,
                transactionRepository,
                walletService,
                paymentTxHelper
        );
        ReflectionTestUtils.setField(paymentService, "mockMode", true);
    }

    @Test
    @DisplayName("같은 결제 완료 요청은 다시 승인하거나 지갑을 충전하지 않는다")
    void completePayment_paidOrder_returnsCurrentBalanceWithoutDuplicateApproval() {
        PaymentOrder paidOrder = order(10L, 1L, "order-1", PaymentStatus.PAID);
        when(orderRepository.findByOrderNo("order-1")).thenReturn(Optional.of(paidOrder));
        when(walletService.getMyWallet(1L))
                .thenReturn(Wallet.builder().userId(1L).starPiece(350).build());

        int balance = paymentService.completePayment(1L, "payment-1", "order-1");

        assertThat(balance).isEqualTo(350);
        verify(paymentTxHelper, never()).writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        );
    }

    @Test
    @DisplayName("READY 결제는 검증 후 승인 처리를 정확히 한 번 위임한다")
    void completePayment_readyOrder_delegatesApprovalOnce() {
        PaymentOrder readyOrder = order(10L, 1L, "order-1", PaymentStatus.READY);
        when(orderRepository.findByOrderNo("order-1")).thenReturn(Optional.of(readyOrder));
        when(paymentTxHelper.writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        )).thenReturn(350);

        int balance = paymentService.completePayment(1L, "payment-1", "order-1");

        assertThat(balance).isEqualTo(350);
        verify(paymentTxHelper).writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        );
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
