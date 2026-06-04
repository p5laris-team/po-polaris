package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.PaymentDto;

@Service
public class PaymentGatewayService {

    @GrpcClient("user")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    public PaymentDto.OrderResponse createOrder(Long userId, int amount, int starPieces) {
        PaymentOrderResponse response = paymentServiceStub.createPaymentOrder(
                CreatePaymentOrderRequest.newBuilder()
                        .setUserId(userId)
                        .setAmount(amount)
                        .setStarPieces(starPieces)
                        .build()
        );

        return new PaymentDto.OrderResponse(
                response.getOrderNo(),
                response.getAmount(),
                response.getStarPieces(),
                response.getStatus()
        );
    }

    public PaymentDto.CompleteResponse completePayment(Long userId, String paymentId, String orderNo) {
        CompletePaymentResponse response = paymentServiceStub.completePayment(
                CompletePaymentRequest.newBuilder()
                        .setUserId(userId)
                        .setPaymentId(paymentId)
                        .setOrderNo(orderNo)
                        .build()
        );

        return new PaymentDto.CompleteResponse(
                response.getSuccess(),
                response.getStarPiece()
        );
    }

    public PaymentDto.RefundResponse refundPayment(Long userId, String orderNo, String reason) {
        RefundPaymentResponse response = paymentServiceStub.refundPayment(
                RefundPaymentRequest.newBuilder()
                        .setUserId(userId)
                        .setOrderNo(orderNo)
                        .setReason(reason)
                        .build()
        );

        return new PaymentDto.RefundResponse(
                response.getSuccess(),
                response.getStarPiece()
        );
    }
}
