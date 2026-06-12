package p5laris.user.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "star_piece_transactions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StarPieceTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; // EARN, SPEND, ADJUST

    @Column(nullable = false)
    private int amount;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(nullable = false, length = 50)
    private String reason; // ATTENDANCE, MISSION_REWARD, etc.

    @Column(name = "ref_type", length = 50)
    private String refType; // ATTENDANCE, MISSION, etc.

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

}
