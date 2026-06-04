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
import p5laris.notification.core.entity.BaseEntity;
import p5laris.notification.domain.domain.enums.FcmPlatform;
import p5laris.notification.domain.domain.enums.FcmTokenDeactivatedReason;

import java.time.LocalDateTime;

@Entity
@Table(name = "fcm_device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmDeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FcmPlatform platform;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "token_updated_at", nullable = false)
    private LocalDateTime tokenUpdatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deactivated_reason", length = 30)
    private FcmTokenDeactivatedReason deactivatedReason;

    @Builder
    public FcmDeviceToken(
            Long userId,
            String fcmToken,
            String tokenHash,
            FcmPlatform platform
    ) {
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.tokenHash = tokenHash;
        this.platform = platform == null ? FcmPlatform.WEB : platform;
        this.active = true;
        this.tokenUpdatedAt = LocalDateTime.now();
    }

    public void refresh(Long userId, String fcmToken, FcmPlatform platform) {
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.platform = platform == null ? FcmPlatform.WEB : platform;
        this.active = true;
        this.tokenUpdatedAt = LocalDateTime.now();
        this.deactivatedAt = null;
        this.deactivatedReason = null;
    }

    public void deactivate(FcmTokenDeactivatedReason reason) {
        this.active = false;
        this.deactivatedAt = LocalDateTime.now();
        this.deactivatedReason = reason == null ? FcmTokenDeactivatedReason.UNKNOWN : reason;
    }
}