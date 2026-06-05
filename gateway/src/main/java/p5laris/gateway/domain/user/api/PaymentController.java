package p5laris.gateway.domain.user.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.PaymentDto;
import p5laris.gateway.domain.user.infrastructure.grpc.PaymentGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentGatewayService paymentGatewayService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-id}")
    private String channelId;

    /**
     * 포트원 결제 설정 조회 (Store ID, Channel ID)
     */
    @GetMapping("/v1/config")
    public ApiResponse<Map<String, String>> getConfig() {
        return ApiResponse.success(Map.of(
                "storeId", storeId,
                "channelId", channelId
        ));
    }

    /**
     * 결제 주문 생성
     */
    @PostMapping("/v1/orders")
    public ApiResponse<PaymentDto.OrderResponse> createOrder(
            @LoginUserId Long userId,
            @Valid @RequestBody PaymentDto.OrderRequest request
    ) {
        return ApiResponse.success(paymentGatewayService.createOrder(
                userId,
                request.getAmount(),
                request.getStarPieces()
        ));
    }

    /**
     * 결제 검증 및 완료 처리
     */
    @PostMapping("/v1/completes")
    public ApiResponse<PaymentDto.CompleteResponse> completePayment(
            @LoginUserId Long userId,
            @Valid @RequestBody PaymentDto.CompleteRequest request
    ) {
        return ApiResponse.success(paymentGatewayService.completePayment(
                userId,
                request.getPaymentId(),
                request.getOrderNo()
        ));
    }
}
