package p5laris.notification.domain.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;
import p5laris.notification.domain.domain.entity.Notification;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;
import p5laris.notification.domain.domain.enums.FcmTokenDeactivatedReason;
import p5laris.notification.domain.domain.enums.PushDeliveryStatus;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationRepository;
import p5laris.notification.domain.domain.repository.NotificationPushDeliveryRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSenderService {

    private static final int DUE_DELIVERY_BATCH_SIZE = 50;
    private static final long DELIVERY_RESERVATION_SECONDS = 60L;
    private static final long RETRY_DELAY_SECONDS = 60L;

    private final NotificationRepository notificationRepository;
    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;
    private final NotificationPushDeliveryRepository notificationPushDeliveryRepository;

    @Async
    public void dispatchPendingDeliveries(Long notificationId) {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationPushDelivery> dueDeliveries = notificationPushDeliveryRepository.findDueByNotificationId(
                notificationId,
                PushDeliveryStatus.PENDING,
                now
        );

        dueDeliveries.forEach(delivery -> dispatchDelivery(delivery.getId()));
    }

    @Scheduled(fixedDelayString = "${notification.push.retry-fixed-delay-ms:30000}")
    public void dispatchDuePendingDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationPushDelivery> dueDeliveries = notificationPushDeliveryRepository.findDue(
                PushDeliveryStatus.PENDING,
                now,
                PageRequest.of(0, DUE_DELIVERY_BATCH_SIZE)
        );

        dueDeliveries.forEach(delivery -> dispatchDelivery(delivery.getId()));
    }

    private void dispatchDelivery(Long deliveryId) {
        LocalDateTime attemptedAt = LocalDateTime.now();
        int reserved = notificationPushDeliveryRepository.reservePendingDelivery(
                deliveryId,
                PushDeliveryStatus.PENDING,
                attemptedAt,
                attemptedAt.plusSeconds(DELIVERY_RESERVATION_SECONDS)
        );
        if (reserved == 0) {
            return;
        }

        NotificationPushDelivery delivery = notificationPushDeliveryRepository.findById(deliveryId)
                .orElse(null);
        if (delivery == null) {
            return;
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElse(null);
        if (notification == null) {
            delivery.markFailed("NOTIFICATION_NOT_FOUND", "Notification row is not found");
            notificationPushDeliveryRepository.save(delivery);
            return;
        }

        if (delivery.getFcmDeviceTokenId() == null) {
            delivery.markSkipped("NO_TARGET_TOKEN", "Delivery has no target FCM token");
            notificationPushDeliveryRepository.save(delivery);
            return;
        }

        FcmDeviceToken token = fcmDeviceTokenRepository.findById(delivery.getFcmDeviceTokenId())
                .orElse(null);
        if (token == null || !token.isActive()) {
            delivery.markSkipped("TOKEN_INACTIVE", "FCM token is missing or inactive");
            notificationPushDeliveryRepository.save(delivery);
            return;
        }

        Message message = Message.builder()
                .setToken(token.getFcmToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build())
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: {} to user: {} token: {}",
                    response, delivery.getUserId(), token.getId());
            delivery.markSent(response);
        } catch (FirebaseMessagingException e) {
            handleFirebaseFailure(delivery, token, e);
        } catch (Exception e) {
            log.error("Unexpected error sending FCM message to user: {}", delivery.getUserId(), e);
            markRetryableFailure(delivery, "UNKNOWN", e.getMessage());
        }

        notificationPushDeliveryRepository.save(delivery);
    }

    static String resolveDeliveryErrorCode(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() != null) {
            return e.getMessagingErrorCode().name();
        }
        if (e.getErrorCode() != null) {
            return e.getErrorCode().name();
        }
        return "UNKNOWN";
    }

    static boolean shouldDeactivateToken(FirebaseMessagingException e) {
        return shouldDeactivateToken(e.getMessagingErrorCode());
    }

    static boolean shouldDeactivateToken(MessagingErrorCode messagingErrorCode) {
        return messagingErrorCode == MessagingErrorCode.UNREGISTERED
                || messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private void handleFirebaseFailure(
            NotificationPushDelivery delivery,
            FcmDeviceToken token,
            FirebaseMessagingException e
    ) {
        String errorCode = resolveDeliveryErrorCode(e);
        log.warn("Failed to send message to user: {} token: {} errorCode: {}",
                delivery.getUserId(), token.getId(), errorCode, e);

        if (shouldDeactivateToken(e)) {
            log.info("Deactivating FCM token: {} due to errorCode: {}", token.getId(), errorCode);
            token.deactivate(FcmTokenDeactivatedReason.TOKEN_INVALID);
            fcmDeviceTokenRepository.save(token);
            delivery.markFailed(errorCode, e.getMessage());
            return;
        }

        markRetryableFailure(delivery, errorCode, e.getMessage());
    }

    private void markRetryableFailure(NotificationPushDelivery delivery, String errorCode, String errorMessage) {
        delivery.markRetryableFailure(
                errorCode,
                errorMessage,
                LocalDateTime.now().plusSeconds(RETRY_DELAY_SECONDS)
        );
    }
}
