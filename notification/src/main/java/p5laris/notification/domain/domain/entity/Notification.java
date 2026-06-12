package p5laris.notification.domain.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import p5laris.common.entity.BaseEntity;
import p5laris.notification.domain.domain.enums.NotificationTargetType;
import p5laris.notification.domain.domain.enums.NotificationType;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id")
    private Long characterId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "push_required", nullable = false)
    private boolean pushRequired;

    @Builder
    public Notification(
            Long userId,
            Long characterId,
            String idempotencyKey,
            NotificationType notificationType,
            String title,
            String message,
            NotificationTargetType targetType,
            Long targetId,
            boolean pushRequired
    ) {
        this.userId = userId;
        this.characterId = characterId;
        this.idempotencyKey = idempotencyKey;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.targetType = targetType;
        this.targetId = targetId;
        this.read = false;
        this.pushRequired = pushRequired;
    }

    public void markRead() {
        if (this.read) {
            return;
        }

        this.read = true;
        this.readAt = LocalDateTime.now();
    }

    public void markUnread() {
        this.read = false;
        this.readAt = null;
    }
}
