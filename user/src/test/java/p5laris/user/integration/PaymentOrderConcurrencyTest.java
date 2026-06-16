package p5laris.user.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import p5laris.user.domain.application.PaymentService;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrderConcurrencyTest extends UserIntegrationTestContainers {

    @Autowired
    private PaymentService paymentService;

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
                .email("payment-concurrency@test.local")
                .nickname("payment-test")
                .provider("TEST")
                .role("USER")
                .status("ACTIVE")
                .build());
        userId = user.getId();
        walletRepository.save(Wallet.builder().userId(userId).starPiece(0).build());
        paymentOrderRepository.save(PaymentOrder.builder()
                .userId(userId)
                .orderNo("concurrent-order")
                .amount(1_000)
                .starPieces(100)
                .status(PaymentStatus.READY)
                .build());
    }

    @Test
    @DisplayName("동일 결제 주문을 동시에 승인해도 거래와 충전은 한 번만 반영된다")
    void concurrentCompletion_createsSinglePaymentAndWalletTransaction() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> completion = () -> {
            ready.countDown();
            start.await();
            return paymentService.completePayment(userId, "payment-1", "concurrent-order");
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = List.of(
                    executor.submit(completion),
                    executor.submit(completion)
            );
            ready.await();
            start.countDown();

            assertThat(futures.get(0).get()).isEqualTo(100);
            assertThat(futures.get(1).get()).isEqualTo(100);
        }

        PaymentOrder order = paymentOrderRepository.findByOrderNo("concurrent-order").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentTransactionRepository.count()).isEqualTo(1);
        assertThat(starPieceTransactionRepository.count()).isEqualTo(1);
        assertThat(walletRepository.findByUserId(userId).orElseThrow().getStarPiece()).isEqualTo(100);
    }
}
