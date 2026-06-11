package p5laris.notification.domain.application;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;
import p5laris.notification.domain.domain.enums.PushDeliveryStatus;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationPushDeliveryRepository;
import p5laris.notification.domain.domain.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmSenderServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmDeviceTokenRepository fcmDeviceTokenRepository;

    @Mock
    private NotificationPushDeliveryRepository notificationPushDeliveryRepository;

    @InjectMocks
    private FcmSenderService fcmSenderService;

    @Test
    void shouldDeactivateToken_returns_true_for_dead_token_errors() {
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.INVALID_ARGUMENT)).isTrue();
    }

    @Test
    void shouldDeactivateToken_returns_false_for_transient_errors() {
        assertThat(FcmSenderService.shouldDeactivateToken(MessagingErrorCode.UNAVAILABLE)).isFalse();
        assertThat(FcmSenderService.shouldDeactivateToken((MessagingErrorCode) null)).isFalse();
    }

    @Test
    void 발송할_PENDING_delivery가_없으면_예약을_시도하지_않는다() {
        when(notificationPushDeliveryRepository.findDueByNotificationId(
                eq(100L),
                eq(PushDeliveryStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        fcmSenderService.dispatchPendingDeliveries(100L);

        verify(notificationPushDeliveryRepository, never()).reservePendingDelivery(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void 다른_스레드가_먼저_예약한_delivery는_발송하지_않는다() {
        NotificationPushDelivery delivery = NotificationPushDelivery.builder()
                .notificationId(100L)
                .userId(1001L)
                .fcmDeviceTokenId(200L)
                .build();
        ReflectionTestUtils.setField(delivery, "id", 300L);

        when(notificationPushDeliveryRepository.findDueByNotificationId(
                eq(100L),
                eq(PushDeliveryStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(delivery));
        when(notificationPushDeliveryRepository.reservePendingDelivery(
                eq(300L),
                eq(PushDeliveryStatus.PENDING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(0);

        fcmSenderService.dispatchPendingDeliveries(100L);

        verify(notificationRepository, never()).findById(any());
        verify(fcmDeviceTokenRepository, never()).findById(any());
    }

    @Test
    void 알림_row가_없으면_재시도하지_않고_FAILED로_종결한다() {
        NotificationPushDelivery delivery = NotificationPushDelivery.builder()
                .notificationId(100L)
                .userId(1001L)
                .fcmDeviceTokenId(200L)
                .build();
        ReflectionTestUtils.setField(delivery, "id", 300L);

        when(notificationPushDeliveryRepository.findDueByNotificationId(
                eq(100L),
                eq(PushDeliveryStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(List.of(delivery));
        when(notificationPushDeliveryRepository.reservePendingDelivery(
                eq(300L),
                eq(PushDeliveryStatus.PENDING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(notificationPushDeliveryRepository.findById(300L)).thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(100L)).thenReturn(Optional.empty());

        fcmSenderService.dispatchPendingDeliveries(100L);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(delivery.getErrorCode()).isEqualTo("NOTIFICATION_NOT_FOUND");
        assertThat(delivery.getNextAttemptAt()).isNull();
        verify(notificationPushDeliveryRepository).save(delivery);
        verify(fcmDeviceTokenRepository, never()).findById(any());
    }
}
