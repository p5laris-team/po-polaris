package p5laris.user.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.user.domain.domain.entity.PaymentOrder;
import p5laris.user.domain.domain.enums.PaymentStatus;
import p5laris.user.domain.domain.repository.PaymentOrderRepository;
import p5laris.user.domain.domain.repository.PaymentTransactionRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class PaymentServiceVerificationTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PaymentTxHelper paymentTxHelper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                orderRepository,
                transactionRepository,
                walletService,
                paymentTxHelper,
                new ObjectMapper(),
                httpClient
        );
        ReflectionTestUtils.setField(paymentService, "storeId", "test-store");
        ReflectionTestUtils.setField(paymentService, "apiSecret", "test-secret");
        ReflectionTestUtils.setField(paymentService, "mockMode", false);
    }

    @Test
    @DisplayName("결제 주문은 READY 상태와 요청 금액으로 저장된다")
    void createOrder_savesReadyOrder() {
        when(orderRepository.save(any(PaymentOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentOrder order = paymentService.createOrder(1L, 1_000, 100);

        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getAmount()).isEqualTo(1_000);
        assertThat(order.getStarPieces()).isEqualTo(100);
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(order.getOrderNo()).startsWith("order_");
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("존재하지 않는 주문은 결제 검증 전에 거부한다")
    void completePayment_missingOrder_rejectsBeforeVerification() {
        when(orderRepository.findByOrderNo("missing")).thenReturn(Optional.empty());

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "missing"),
                UserErrorCode.PAYMENT_ORDER_NOT_FOUND
        );

        verify(paymentTxHelper, never()).writePaymentApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자의 주문은 존재하지 않는 주문처럼 거부한다")
    void completePayment_otherOwner_rejectsBeforeVerification() {
        when(orderRepository.findByOrderNo("order-1"))
                .thenReturn(Optional.of(order(2L, PaymentStatus.READY)));

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_ORDER_NOT_FOUND
        );
    }

    @Test
    @DisplayName("실패 처리된 주문은 다시 승인할 수 없다")
    void completePayment_failedOrder_rejectsReprocessing() {
        when(orderRepository.findByOrderNo("order-1"))
                .thenReturn(Optional.of(order(1L, PaymentStatus.FAILED)));

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_ALREADY_PROCESSED
        );
    }

    @Test
    @DisplayName("PortOne 응답 코드가 200이 아니면 주문을 실패 처리한다")
    void completePayment_portOneError_marksFailure() throws Exception {
        stubReadyOrder();
        stubResponse(503, "{\"message\":\"unavailable\"}");

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_TRANSACTION_FAILED
        );

        verify(paymentTxHelper).writePaymentFailure("order-1", PaymentStatus.FAILED);
        verify(paymentTxHelper, never()).writePaymentApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PortOne 결제 상태가 PAID가 아니면 주문을 실패 처리한다")
    void completePayment_unpaidStatus_marksFailure() throws Exception {
        stubReadyOrder();
        stubResponse(200, """
                {"status":"FAILED","amount":{"total":1000}}
                """);

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_TRANSACTION_FAILED
        );

        verify(paymentTxHelper).writePaymentFailure("order-1", PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("PortOne 결제 금액이 주문과 다르면 주문을 실패 처리한다")
    void completePayment_amountMismatch_marksFailure() throws Exception {
        stubReadyOrder();
        stubResponse(200, """
                {"status":"PAID","amount":{"total":2000}}
                """);

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_AMOUNT_MISMATCH
        );

        verify(paymentTxHelper).writePaymentFailure("order-1", PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("PortOne 응답 파싱 실패는 거래 실패로 변환하고 주문을 실패 처리한다")
    void completePayment_invalidResponse_marksFailure() throws Exception {
        stubReadyOrder();
        stubResponse(200, "{invalid-json");

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_TRANSACTION_FAILED
        );

        verify(paymentTxHelper).writePaymentFailure("order-1", PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("PortOne 네트워크 오류는 거래 실패로 변환하고 주문을 실패 처리한다")
    void completePayment_networkError_marksFailure() throws Exception {
        stubReadyOrder();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        assertPaymentError(
                () -> paymentService.completePayment(1L, "payment-1", "order-1"),
                UserErrorCode.PAYMENT_TRANSACTION_FAILED
        );

        verify(paymentTxHelper).writePaymentFailure("order-1", PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("PortOne 검증 성공 시 실제 PG사와 결제수단으로 승인을 위임한다")
    void completePayment_validResponse_delegatesApproval() throws Exception {
        stubReadyOrder();
        stubResponse(200, """
                {
                  "status":"PAID",
                  "amount":{"total":1000},
                  "pgProvider":"KAKAOPAY",
                  "payMethod":"EASY_PAY"
                }
                """);
        when(paymentTxHelper.writePaymentApproval(
                1L, "order-1", "payment-1", "KAKAOPAY", "EASY_PAY"
        )).thenReturn(450);

        int balance = paymentService.completePayment(1L, "payment-1", "order-1");

        assertThat(balance).isEqualTo(450);
        verify(paymentTxHelper, never()).writePaymentFailure(any(), any());
    }

    @Test
    @DisplayName("PortOne 응답에 PG 정보가 없으면 기본값으로 승인을 위임한다")
    void completePayment_validResponseWithoutProvider_usesDefaults() throws Exception {
        stubReadyOrder();
        stubResponse(200, """
                {"status":"PAID","amount":{"total":1000}}
                """);
        when(paymentTxHelper.writePaymentApproval(
                1L, "order-1", "payment-1", "TOSS_PAYMENTS", "CARD"
        )).thenReturn(450);

        int balance = paymentService.completePayment(1L, "payment-1", "order-1");

        assertThat(balance).isEqualTo(450);
    }

    private void stubReadyOrder() {
        when(orderRepository.findByOrderNo("order-1"))
                .thenReturn(Optional.of(order(1L, PaymentStatus.READY)));
    }

    private void stubResponse(int status, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    private PaymentOrder order(Long userId, PaymentStatus status) {
        return PaymentOrder.builder()
                .id(10L)
                .userId(userId)
                .orderNo("order-1")
                .amount(1_000)
                .starPieces(100)
                .status(status)
                .build();
    }

    private void assertPaymentError(Runnable invocation, UserErrorCode errorCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
