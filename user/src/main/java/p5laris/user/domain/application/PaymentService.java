package p5laris.user.domain.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.entity.PaymentTransaction;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PaymentTxHelper paymentTxHelper;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.api-secret}")
    private String apiSecret;

    @Value("${portone.mock-mode:false}")
    private boolean mockMode;

    @Autowired
    public PaymentService(
            PaymentOrderRepository orderRepository,
            PaymentTransactionRepository transactionRepository,
            WalletService walletService,
            PaymentTxHelper paymentTxHelper
    ) {
        this(
                orderRepository,
                transactionRepository,
                walletService,
                paymentTxHelper,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
    }

    PaymentService(
            PaymentOrderRepository orderRepository,
            PaymentTransactionRepository transactionRepository,
            WalletService walletService,
            PaymentTxHelper paymentTxHelper,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.paymentTxHelper = paymentTxHelper;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * 결제 주문을 생성합니다. (결제창 오픈 전 단계)
     * 고유한 주문 번호(orderNo)를 생성하고 초기 상태 READY로 DB에 커밋합니다.
     *
     * @param userId 유저 식별자
     * @param amount 결제할 원화 금액
     * @param starPieces 충전 대상 별조각 수
     * @return 생성된 결제 주문 엔티티
     */
    @Transactional
    public PaymentOrder createOrder(Long userId, int amount, int starPieces) {
        // 고유 주문 번호 규칙 설정 (order_시간밀리초_난수8자리)
        String orderNo = "order_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        PaymentOrder order = PaymentOrder.builder()
                .userId(userId)
                .orderNo(orderNo)
                .amount(amount)
                .starPieces(starPieces)
                .status(PaymentStatus.READY)
                .build();

        return orderRepository.save(order);
    }

    /**
     * 포트원 결제 완료 검증 및 비즈니스 충전을 통합 처리합니다.
     * 커넥션 풀 누수 및 락 대기를 최소화하기 위해 외부 API 호출(포트원 결제 조회)은 
     * Non-Transactional(트랜잭션 미적용) 상태로 먼저 수행한 뒤,
     * 검증이 통과된 건만 단일 로컬 DB 쓰기 트랜잭션으로 원자적 승인을 진행합니다.
     *
     * @param userId 유저 식별자
     * @param paymentId 포트원 결제 고유 ID
     * @param orderNo 상점 주문 번호
     * @return 충전 완료 후 유저의 최종 별조각 보유량
     */
    public int completePayment(Long userId, String paymentId, String orderNo) {
        // 1. 기존 주문 정보 확인
        PaymentOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 2. 소유권 확인
        if (!order.getUserId().equals(userId)) {
            throw new UserException(UserErrorCode.PAYMENT_ORDER_NOT_FOUND);
        }

        // 3. 이미 결제 완료된 건에 대해서는 멱등적으로 성공 결과 즉시 리턴 (중복 처리 방지)
        if (order.getStatus() == PaymentStatus.PAID) {
            return walletService.getMyWallet(userId).getStarPiece();
        }

        // 4. READY(대기) 상태가 아닌 결제 주문은 중복 요청 또는 실패 건이므로 차단
        if (order.getStatus() != PaymentStatus.READY) {
            throw new UserException(UserErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        String pgProvider = "TOSS_PAYMENTS";
        String payMethod = "CARD";

        // 5. 실결제 환경인 경우 포트원 V2 조회 API를 이용해 결제 정합성(금액, 상태) 검증
        if (!mockMode) {
            try {
                // 포트원 V2 결제건 단건 조회 (외부 API 통신 - 트랜잭션 외부에서 작동)
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.portone.io/payments/" + paymentId))
                        .header("Authorization", "PortOne " + apiSecret)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.error("Portone V2 API response error. Status: {}, Body: {}", response.statusCode(), response.body());
                    throw new UserException(UserErrorCode.PAYMENT_TRANSACTION_FAILED);
                }

                JsonNode rootNode = objectMapper.readTree(response.body());
                String status = rootNode.path("status").asText();
                int totalAmount = rootNode.path("amount").path("total").asInt();
                
                pgProvider = rootNode.path("pgProvider").asText("TOSS_PAYMENTS");
                payMethod = rootNode.path("payMethod").asText("CARD");

                // 상태 및 결제 금액 정합성 검증
                if (!"PAID".equalsIgnoreCase(status)) {
                    log.warn("Payment status is not PAID. Actual status: {}", status);
                    throw new UserException(UserErrorCode.PAYMENT_TRANSACTION_FAILED);
                }

                if (totalAmount != order.getAmount()) {
                    log.warn("Payment amount mismatch. Order amount: {}, Actual amount: {}", order.getAmount(), totalAmount);
                    throw new UserException(UserErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }

            } catch (UserException ue) {
                // 검증 예외 발생 시 전용 트랜잭션 헬퍼를 통해 상태를 FAILED로 안전하게 저장
                paymentTxHelper.writePaymentFailure(orderNo, PaymentStatus.FAILED);
                throw ue;
            } catch (Exception e) {
                log.error("Portone V2 verification failed due to exception", e);
                // 네트워크 에러 등 발생 시 FAILED 처리
                paymentTxHelper.writePaymentFailure(orderNo, PaymentStatus.FAILED);
                throw new UserException(UserErrorCode.PAYMENT_TRANSACTION_FAILED);
            }
        } else {
            log.info("[MOCK MODE] Payment verification bypassed for orderNo={}", orderNo);
        }

        // 6. 포트원 검증 성공 -> 최종 DB 원자적 쓰기 실행 (주문 완료, 상세 내역 생성, 지갑 별조각 적립 일괄 커밋)
        return paymentTxHelper.writePaymentApproval(userId, orderNo, paymentId, pgProvider, payMethod);
    }


}
