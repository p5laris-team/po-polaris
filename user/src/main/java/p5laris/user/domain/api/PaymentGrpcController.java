package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.PaymentService;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.exception.UserException;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class PaymentGrpcController extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "결제 서비스 처리 중 오류가 발생했습니다.";
    private final PaymentService paymentService;

    @Override
    public void createPaymentOrder(CreatePaymentOrderRequest request, StreamObserver<PaymentOrderResponse> responseObserver) {
        try {
            PaymentOrder order = paymentService.createOrder(
                    request.getUserId(),
                    request.getAmount(),
                    request.getStarPieces()
            );

            PaymentOrderResponse response = PaymentOrderResponse.newBuilder()
                    .setOrderNo(order.getOrderNo())
                    .setAmount(order.getAmount())
                    .setStarPieces(order.getStarPieces())
                    .setStatus(order.getStatus().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("결제 주문 생성", e));
        }
    }

    @Override
    public void completePayment(CompletePaymentRequest request, StreamObserver<CompletePaymentResponse> responseObserver) {
        try {
            int updatedStarPieces = paymentService.completePayment(
                    request.getUserId(),
                    request.getPaymentId(),
                    request.getOrderNo()
            );

            CompletePaymentResponse response = CompletePaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setStarPiece(updatedStarPieces)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("결제 검증 및 완료", e));
        }
    }

    @Override
    public void refundPayment(RefundPaymentRequest request, StreamObserver<RefundPaymentResponse> responseObserver) {
        try {
            int updatedStarPieces = paymentService.refundPayment(
                    request.getUserId(),
                    request.getOrderNo(),
                    request.getReason()
            );

            RefundPaymentResponse response = RefundPaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setStarPiece(updatedStarPieces)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("결제 환불 처리", e));
        }
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("결제 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return io.grpc.Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }
}
