package p5laris.user.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.user.core.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_order_id", nullable = false)
    private Long paymentOrderId;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    @Column(name = "pg_provider", length = 50)
    private String pgProvider;

    @Column(name = "pay_method", length = 50)
    private String payMethod;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_amount")
    private Integer cancelAmount;

    public void cancel(LocalDateTime cancelledAt, int cancelAmount) {
        this.cancelledAt = cancelledAt;
        this.cancelAmount = cancelAmount;
    }
}
