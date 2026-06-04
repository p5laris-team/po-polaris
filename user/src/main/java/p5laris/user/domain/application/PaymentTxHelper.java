package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.PaymentTransaction;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTxHelper {

    private final PaymentOrderRepository orderRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final WalletService walletService;

    /**
     * 하나의 로컬 데이터베이스 트랜잭션 내에서 결제 상태 업데이트 및 지갑 적립을 원자적으로 수행합니다.
     */
    @Transactional
    public int writePaymentApproval(Long userId, String orderNo, String paymentId, String pgProvider, String payMethod) {
        PaymentOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND);
        }

        if (order.getStatus() == PaymentStatus.PAID) {
            return walletService.getMyWallet(userId).getStarPiece();
        }

        if (order.getStatus() != PaymentStatus.READY) {
            throw new UserException(UserErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 1. 주문 상태 PAID 변경
        order.updateStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // 2. 결제 상세 내역 기록
        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentOrderId(order.getId())
                .paymentId(paymentId)
                .pgProvider(pgProvider)
                .payMethod(payMethod)
                .paidAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        // 3. 지갑 별조각 충전 및 지갑 거래이력 생성 (동일 트랜잭션)
        walletService.earnStarPiece(
                userId,
                order.getStarPieces(),
                "PAYMENT_CHARGE",
                "PAYMENT_ORDER",
                order.getId(),
                "PAYMENT:" + orderNo
        );

        log.info("Payment approved atomically. orderNo={}, userId={}, starPieces={}", orderNo, userId, order.getStarPieces());
        return walletService.getMyWallet(userId).getStarPiece();
    }
}
