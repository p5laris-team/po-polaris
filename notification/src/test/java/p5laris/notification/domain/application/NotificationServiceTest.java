package p5laris.notification.domain.application;

import com.p5laris.proto.notification.v1.MarkAllNotificationsReadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationRepository;
import p5laris.notification.domain.domain.repository.NotificationSettingRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private NotificationService notificationService;

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
}
