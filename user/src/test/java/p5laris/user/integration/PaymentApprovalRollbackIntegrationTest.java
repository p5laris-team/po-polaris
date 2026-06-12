package p5laris.user.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import p5laris.user.domain.application.PaymentTxHelper;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApprovalRollbackIntegrationTest extends UserIntegrationTestContainers {

    @Autowired
    private PaymentTxHelper paymentTxHelper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private StarPieceTransactionRepository starPieceTransactionRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        starPieceTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .email("payment-rollback@test.local")
                .nickname("rollback-test")
                .provider("TEST")
                .role("USER")
                .status("ACTIVE")
                .build());
        userId = user.getId();
        walletRepository.save(Wallet.builder().userId(userId).starPiece(0).build());
        paymentOrderRepository.save(PaymentOrder.builder()
                .userId(userId)
                .orderNo("rollback-order")
                .amount(1_000)
                .starPieces(100)
                .status(PaymentStatus.READY)
                .build());
    }

    @Test
    @DisplayName("결제 거래 저장이 실패하면 주문 상태와 지갑 충전도 함께 롤백된다")
    void approvalFailure_rollsBackOrderTransactionAndWallet() {
        assertThatThrownBy(() -> paymentTxHelper.writePaymentApproval(
                userId,
                "rollback-order",
                null,
                "TOSS_PAYMENTS",
                "CARD"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(paymentOrderRepository.findByOrderNo("rollback-order").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.READY);
        assertThat(paymentTransactionRepository.count()).isZero();
        assertThat(starPieceTransactionRepository.count()).isZero();
        assertThat(walletRepository.findByUserId(userId).orElseThrow().getStarPiece()).isZero();
    }
}
