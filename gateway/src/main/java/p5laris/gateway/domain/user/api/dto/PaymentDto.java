package p5laris.gateway.domain.user.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PaymentDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRequest {
        @NotNull(message = "결제 금액은 필수입니다.")
        @Min(value = 100, message = "최소 결제 금액은 100원입니다.")
        private Integer amount;

        @NotNull(message = "별조각 개수는 필수입니다.")
        @Min(value = 1, message = "최소 충전 별조각은 1개입니다.")
        private Integer starPieces;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private String orderNo;
        private int amount;
        private int starPieces;
        private String status;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        @NotBlank(message = "paymentId는 필수입니다.")
        private String paymentId;

        @NotBlank(message = "orderNo는 필수입니다.")
        private String orderNo;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteResponse {
        private boolean success;
        private int starPiece;
    }
}
