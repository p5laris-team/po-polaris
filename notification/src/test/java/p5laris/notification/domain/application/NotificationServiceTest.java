package p5laris.notification.domain.application;

import com.p5laris.proto.notification.v1.MarkAllNotificationsReadResponse;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.notification.domain.application.event.NotificationDeliveryDecision;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;
import p5laris.notification.domain.domain.entity.Notification;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;
import p5laris.notification.domain.domain.entity.NotificationSetting;
import p5laris.notification.domain.domain.enums.FcmPlatform;
import p5laris.notification.domain.domain.enums.FcmTokenDeactivatedReason;
import p5laris.notification.domain.domain.enums.NotificationType;
import p5laris.notification.domain.domain.enums.PushDeliveryStatus;
import p5laris.notification.domain.exception.NotificationException;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationPushDeliveryRepository;
import p5laris.notification.domain.domain.repository.NotificationRepository;
import p5laris.notification.domain.domain.repository.NotificationSettingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmDeviceTokenRepository fcmDeviceTokenRepository;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private NotificationPushDeliveryRepository notificationPushDeliveryRepository;

    @Mock
    private NotificationDeliveryPolicy notificationDeliveryPolicy;

    @InjectMocks
    private NotificationService notificationService;

    private SendPushNotificationRequest pushRequest;

    @BeforeEach
    void setUp() {
        pushRequest = SendPushNotificationRequest.newBuilder()
                .setUserId(1001L)
                .setTitle("새 미션이 도착했어요")
                .setBody("물 한 잔 마시기 미션을 해볼까요?")
                .setNotificationType(com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_MISSION)
                .build();
    }

    @Test
    void 모든_알림_읽음은_안_읽은_알림만_일괄_처리하고_남은_개수를_반환한다() {
        Long userId = 1001L;
        when(notificationRepository.markAllReadByUserId(
                eq(userId),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(3);
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(0L);

        MarkAllNotificationsReadResponse response = notificationService.markAllNotificationsRead(userId);

        assertThat(response.getUpdatedCount()).isEqualTo(3);
        assertThat(response.getUnreadCount()).isZero();
        assertThat(response.getUpdatedAt()).isNotBlank();
        verify(notificationRepository).markAllReadByUserId(
                eq(userId),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        verify(notificationRepository).countByUserIdAndReadFalse(userId);
    }

    @Test
    void 동일한_멱등키가_이미_있으면_알림과_발송이력을_새로_만들지_않는다() {
        String idempotencyKey = "notification-key-1";
        Notification existingNotification = Notification.builder()
                .userId(1001L)
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.MISSION)
                .title("새 미션이 도착했어요")
                .message("물 한 잔 마시기 미션을 해볼까요?")
                .pushRequired(true)
                .build();
        when(notificationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingNotification));

        Notification result = notificationService.createNotification(pushRequest, idempotencyKey);

        assertThat(result).isSameAs(existingNotification);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationPushDeliveryRepository, never()).save(any(NotificationPushDelivery.class));
    }

    @Test
    void proto_request의_멱등키로_중복_알림을_재사용한다() {
        String idempotencyKey = "notification-key-1";
        SendPushNotificationRequest requestWithKey = pushRequest.toBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        Notification existingNotification = Notification.builder()
                .userId(1001L)
                .idempotencyKey(idempotencyKey)
                .notificationType(NotificationType.MISSION)
                .title("새 미션이 도착했어요")
                .message("물 한 잔 마시기 미션을 해볼까요?")
                .pushRequired(true)
                .build();
        when(notificationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingNotification));

        Notification result = notificationService.createNotification(requestWithKey);

        assertThat(result).isSameAs(existingNotification);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationPushDeliveryRepository, never()).save(any(NotificationPushDelivery.class));
    }

    @Test
    void 새_푸시_알림은_알림과_PENDING_발송이력을_같은_흐름에서_저장한다() {
        String idempotencyKey = "notification-key-1";
        FcmDeviceToken token = FcmDeviceToken.builder()
                .userId(1001L)
                .fcmToken("fcm-token")
                .tokenHash("token-hash")
                .platform(FcmPlatform.WEB)
                .build();
        ReflectionTestUtils.setField(token, "id", 200L);

        when(notificationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 100L);
            return notification;
        });
        when(notificationSettingRepository.findByUserId(1001L))
                .thenReturn(Optional.of(NotificationSetting.defaultSetting(1001L)));
        when(notificationDeliveryPolicy.decide(any(NotificationSetting.class), eq(NotificationType.MISSION)))
                .thenReturn(NotificationDeliveryDecision.allowed());
        when(fcmDeviceTokenRepository.findByUserIdAndActiveTrue(1001L)).thenReturn(List.of(token));

        Notification result = notificationService.createNotification(pushRequest, idempotencyKey);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);

        ArgumentCaptor<NotificationPushDelivery> deliveryCaptor =
                ArgumentCaptor.forClass(NotificationPushDelivery.class);
        verify(notificationPushDeliveryRepository).save(deliveryCaptor.capture());

        NotificationPushDelivery delivery = deliveryCaptor.getValue();
        assertThat(delivery.getNotificationId()).isEqualTo(100L);
        assertThat(delivery.getUserId()).isEqualTo(1001L);
        assertThat(delivery.getFcmDeviceTokenId()).isEqualTo(200L);
        assertThat(delivery.getDeliveryStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isNotNull();
    }
    @Test
    void registeringDeactivatedToken_reactivatesExistingRow() {
        FcmDeviceToken existingToken = FcmDeviceToken.builder()
                .userId(999L)
                .fcmToken("same-token")
                .tokenHash("existing-hash")
                .platform(FcmPlatform.WEB)
                .build();
        ReflectionTestUtils.setField(existingToken, "id", 200L);
        existingToken.deactivate(FcmTokenDeactivatedReason.TOKEN_INVALID);
        when(fcmDeviceTokenRepository.findByTokenHash(any(String.class)))
                .thenReturn(Optional.of(existingToken));

        var response = notificationService.registerFcmToken(1001L, "same-token");

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(existingToken.getUserId()).isEqualTo(1001L);
        assertThat(existingToken.isActive()).isTrue();
        assertThat(existingToken.getDeactivatedAt()).isNull();
        assertThat(existingToken.getDeactivatedReason()).isNull();
        verify(fcmDeviceTokenRepository, never()).save(any(FcmDeviceToken.class));
    }

    @Test
    void registeringBlankToken_rejectsWithoutPersistence() {
        assertThatThrownBy(() -> notificationService.registerFcmToken(1001L, "  "))
                .isInstanceOf(NotificationException.class);

        verify(fcmDeviceTokenRepository, never()).findByTokenHash(any());
        verify(fcmDeviceTokenRepository, never()).save(any());
    }

    @Test
    void createNotification_withoutActiveTokens_recordsSkippedDelivery() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 100L);
            return notification;
        });
        when(notificationSettingRepository.findByUserId(1001L))
                .thenReturn(Optional.of(NotificationSetting.defaultSetting(1001L)));
        when(notificationDeliveryPolicy.decide(any(NotificationSetting.class), eq(NotificationType.MISSION)))
                .thenReturn(NotificationDeliveryDecision.allowed());
        when(fcmDeviceTokenRepository.findByUserIdAndActiveTrue(1001L)).thenReturn(List.of());

        notificationService.createNotification(pushRequest, null);

        ArgumentCaptor<NotificationPushDelivery> deliveryCaptor =
                ArgumentCaptor.forClass(NotificationPushDelivery.class);
        verify(notificationPushDeliveryRepository).save(deliveryCaptor.capture());

        NotificationPushDelivery delivery = deliveryCaptor.getValue();
        assertThat(delivery.getDeliveryStatus()).isEqualTo(PushDeliveryStatus.SKIPPED);
        assertThat(delivery.getFcmDeviceTokenId()).isNull();
        assertThat(delivery.getErrorCode()).isEqualTo("NO_ACTIVE_TOKENS");
        assertThat(delivery.getNextAttemptAt()).isNull();
    }
}
