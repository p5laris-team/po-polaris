package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

@Entity
@Table(name = "user_items")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    public void addQuantity(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        this.quantity += amount;
    }

    public void useQuantity(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (this.quantity < amount) throw new IllegalStateException("Not enough quantity");
        this.quantity -= amount;
    }
}
