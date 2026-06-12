package p5laris.notification.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.common.entity.BaseEntity;
import p5laris.notification.domain.domain.enums.PushDeliveryStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_push_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPushDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_device_token_id")
    private Long fcmDeviceTokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private PushDeliveryStatus deliveryStatus;

    @Column(name = "fcm_message_id", length = 255)
    private String fcmMessageId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Builder
    public NotificationPushDelivery(
            Long notificationId,
            Long userId,
            Long fcmDeviceTokenId
    ) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.fcmDeviceTokenId = fcmDeviceTokenId;
        this.deliveryStatus = PushDeliveryStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
        this.attemptCount = 0;
    }

    public void markAttempted() {
        markAttempted(LocalDateTime.now(), LocalDateTime.now());
    }

    public void markAttempted(LocalDateTime attemptedAt, LocalDateTime nextAttemptAt) {
        this.attemptedAt = attemptedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount++;
    }

    public void markSent(String fcmMessageId) {
        this.deliveryStatus = PushDeliveryStatus.SENT;
        this.fcmMessageId = fcmMessageId;
        this.sentAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.failedAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.deliveryStatus = PushDeliveryStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.failedAt = LocalDateTime.now();
        this.nextAttemptAt = null;
    }

    public void markRetryableFailure(String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        this.deliveryStatus = PushDeliveryStatus.PENDING;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.failedAt = LocalDateTime.now();
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markSkipped(String errorCode, String errorMessage) {
        this.deliveryStatus = PushDeliveryStatus.SKIPPED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.nextAttemptAt = null;
    }
}
