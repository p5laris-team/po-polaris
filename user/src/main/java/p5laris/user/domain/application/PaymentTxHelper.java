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
     * [성공 시나리오 처리]
     * 하나의 로컬 데이터베이스 트랜잭션 내에서 결제 상태 업데이트, 거래 기록 저장, 지갑 충전을 원자적으로 수행합니다.
     * 외부 HTTP 통신이 차단된 순수한 DB 쓰기만 모아두어 커넥션 점유 및 락 획득 시간을 최소화합니다.
     *
     * @param userId 유저 식별자
     * @param orderNo 상점 주문 번호
     * @param paymentId 포트원 결제 식별 키
     * @param pgProvider 결제 PG사 정보
     * @param payMethod 결제 수단
     * @return 충전 완료 후의 총 별조각 개수
     */
    @Transactional
    public int writePaymentApproval(Long userId, String orderNo, String paymentId, String pgProvider, String payMethod) {
        // 1. 주문 건 재조회
        PaymentOrder order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 2. 주문 소유주 검증
        if (!order.getUserId().equals(userId)) {
            throw new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND);
        }

        // 3. 이미 성공 처리된 건에 대한 멱등성 반환
        if (order.getStatus() == PaymentStatus.PAID) {
            return walletService.getMyWallet(userId).getStarPiece();
        }

        // 4. 주문이 READY 상태가 아니면 이미 처리/실패된 건이므로 예외 처리
        if (order.getStatus() != PaymentStatus.READY) {
            throw new UserException(UserErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 5. 주문 상태를 PAID로 변경 및 영속화
        order.updateStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // 6. 결제 거래 내역 상세 기록 생성
        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentOrderId(order.getId())
                .paymentId(paymentId)
                .pgProvider(pgProvider)
                .payMethod(payMethod)
                .paidAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        // 7. 지갑에 충전액 가산 및 지갑 트랜잭션 영속화 (동일 DB 트랜잭션 바운더리)
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

    /**
     * [실패 시나리오 처리]
     * 결제 검증(외부 API 호출) 중 발생한 실패 상태를 트랜잭션을 열어 안전하고 원자적으로 DB에 업데이트합니다.
     *
     * @param orderNo 상점 주문 번호
     * @param status 변경할 결제 상태 (FAILED 등)
     */
    @Transactional
    public void writePaymentFailure(String orderNo, PaymentStatus status) {
        orderRepository.findByOrderNoForUpdate(orderNo).ifPresent(order -> {
            if (order.getStatus() != PaymentStatus.READY) {
                log.info("Payment failure update skipped because order is already processed. orderNo={}, status={}",
                        orderNo, order.getStatus());
                return;
            }
            order.updateStatus(status);
            orderRepository.save(order);
            log.info("Payment status updated to FAILED. orderNo={}", orderNo);
        });
    }
}
