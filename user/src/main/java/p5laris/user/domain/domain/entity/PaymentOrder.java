package p5laris.user.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;
import p5laris.user.domain.domain.enums.PaymentStatus;

@Entity
@Table(name = "payment_orders")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_no", nullable = false, unique = true, length = 100)
    private String orderNo;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "star_pieces", nullable = false)
    private int starPieces;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }
}
