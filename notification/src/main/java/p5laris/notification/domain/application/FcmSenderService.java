package p5laris.notification.domain.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;
import p5laris.notification.domain.domain.enums.FcmTokenDeactivatedReason;
import p5laris.notification.domain.domain.enums.NotificationType;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationPushDeliveryRepository;
import p5laris.notification.domain.domain.repository.NotificationSettingRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSenderService {

    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationPushDeliveryRepository notificationPushDeliveryRepository;
    private final NotificationDeliveryPolicy notificationDeliveryPolicy;

    @Async
    @Transactional
    public void sendPushNotification(
            Long notificationId,
            Long userId,
            String title,
            String body,
            NotificationType notificationType
    ) {
        // 수신 동의 여부 확인
        p5laris.notification.domain.domain.entity.NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> p5laris.notification.domain.domain.entity.NotificationSetting.defaultSetting(userId));

        NotificationDeliveryDecision decision = notificationDeliveryPolicy.decide(setting, notificationType);
        if (!decision.sendable()) {
            log.info("Push notification skipped. userId={}, notificationId={}, reason={}",
                    userId, notificationId, decision.skipCode());
            recordSkippedDelivery(notificationId, userId, decision.skipCode(), decision.skipMessage());
            return;
        }

        List<FcmDeviceToken> activeTokens = fcmDeviceTokenRepository.findByUserIdAndActiveTrue(userId);

        if (activeTokens.isEmpty()) {
            log.info("No active FCM tokens found for user: {}", userId);
            recordSkippedDelivery(notificationId, userId, "NO_ACTIVE_TOKENS", "User has no active FCM tokens");
            return;
        }

        for (FcmDeviceToken token : activeTokens) {
            NotificationPushDelivery delivery = NotificationPushDelivery.builder()
                    .notificationId(notificationId)
                    .userId(userId)
                    .fcmDeviceTokenId(token.getId())
                    .build();
            delivery.markAttempted();
            notificationPushDeliveryRepository.save(delivery);

            Message message = Message.builder()
                    .setToken(token.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent message: {} to user: {} token: {}", response, userId, token.getId());
                delivery.markSent(response);
            } catch (FirebaseMessagingException e) {
                log.warn("Failed to send message to user: {} token: {}", userId, token.getId(), e);
                
                String errorCode = e.getErrorCode().name();
                delivery.markFailed(errorCode, e.getMessage());

                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    log.info("Deactivating FCM token: {} due to errorCode: {}", token.getId(), errorCode);
                    token.deactivate(FcmTokenDeactivatedReason.UNKNOWN);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM message to user: {}", userId, e);
                delivery.markFailed("UNKNOWN", e.getMessage());
            }

            notificationPushDeliveryRepository.save(delivery);
        }
    }

    private void recordSkippedDelivery(Long notificationId, Long userId, String errorCode, String errorMessage) {
        NotificationPushDelivery delivery = NotificationPushDelivery.builder()
                .notificationId(notificationId)
                .userId(userId)
                .fcmDeviceTokenId(null)
                .build();
        delivery.markSkipped(errorCode, errorMessage);
        notificationPushDeliveryRepository.save(delivery);
    }
}
