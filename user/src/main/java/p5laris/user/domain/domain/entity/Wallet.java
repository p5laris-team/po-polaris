package p5laris.user.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

@Entity
@Table(name = "wallets")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "star_piece", nullable = false)
    @Builder.Default
    private int starPiece = 0;

    public void addStarPiece(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        this.starPiece += amount;
    }

    public void useStarPiece(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        if (this.starPiece < amount) {
            throw new IllegalStateException("Not enough star pieces");
        }
        this.starPiece -= amount;
    }
}
