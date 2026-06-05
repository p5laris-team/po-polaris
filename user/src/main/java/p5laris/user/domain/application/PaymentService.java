package p5laris.user.domain.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PaymentTxHelper paymentTxHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.api-secret}")
    private String apiSecret;

    @Value("${portone.mock-mode:false}")
    private boolean mockMode;

    /**
     * 결제 주문 생성
     */
    @Transactional
    public PaymentOrder createOrder(Long userId, int amount, int starPieces) {
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
     * 결제 완료 처리 및 검증
     */
    public int completePayment(Long userId, String paymentId, String orderNo) {
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

        String pgProvider = "TOSS_PAYMENTS";
        String payMethod = "CARD";

        if (!mockMode) {
            try {
                // 포트원 V2 결제 상세 조회 API 호출 (트랜잭션 외부에서 수행)
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
                order.updateStatus(PaymentStatus.FAILED);
                orderRepository.save(order);
                throw ue;
            } catch (Exception e) {
                log.error("Portone V2 verification failed due to exception", e);
                order.updateStatus(PaymentStatus.FAILED);
                orderRepository.save(order);
                throw new UserException(UserErrorCode.PAYMENT_TRANSACTION_FAILED);
            }
        } else {
            log.info("[MOCK MODE] Payment verification bypassed for orderNo={}", orderNo);
        }

        // 포트원 결제 검증 통과 완료 -> 단일 로컬 DB 트랜잭션으로 원자적 쓰기 위임
        return paymentTxHelper.writePaymentApproval(userId, orderNo, paymentId, pgProvider, payMethod);
    }


}
