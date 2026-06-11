package p5laris.notification.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.notification.domain.domain.entity.Notification;
import p5laris.notification.domain.domain.enums.NotificationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private FcmSenderService fcmSenderService;

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(notificationService, fcmSenderService, new ObjectMapper());
        Notification notification = Notification.builder()
                .userId(1001L)
                .notificationType(NotificationType.MISSION)
                .title("새 미션이 도착했어요")
                .message("물 한 잔 마시기 미션을 해볼까요?")
                .pushRequired(true)
                .build();
        ReflectionTestUtils.setField(notification, "id", 100L);

        when(notificationService.createNotification(any(SendPushNotificationRequest.class), anyString()))
                .thenReturn(notification);
    }

    @Test
    void V2_알림_payload는_target을_proto_request에_담는다() {
        String payload = """
                {
                  "userId": 1001,
                  "title": "새 미션이 도착했어요",
                  "body": "물 한 잔 마시기 미션을 해볼까요?",
                  "notificationType": "MISSION",
                  "targetType": "MISSION",
                  "targetId": 2001
                }
                """;

        consumer.consumeNotificationRequest(payload, "notification-key-1");

        ArgumentCaptor<SendPushNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(SendPushNotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture(), eq("notification-key-1"));
        verify(fcmSenderService).dispatchPendingDeliveries(100L);

        SendPushNotificationRequest request = requestCaptor.getValue();
        assertThat(request.getUserId()).isEqualTo(1001L);
        assertThat(request.getNotificationType().name()).isEqualTo("NOTIFICATION_TYPE_MISSION");
        assertThat(request.hasTargetType()).isTrue();
        assertThat(request.getTargetType().name()).isEqualTo("TARGET_TYPE_MISSION");
        assertThat(request.hasTargetId()).isTrue();
        assertThat(request.getTargetId()).isEqualTo(2001L);
    }

    @Test
    void V1_알림_payload도_target_없이_처리한다() {
        String payload = """
                {
                  "userId": 1001,
                  "title": "출석 체크",
                  "body": "오늘도 별조각을 받아볼까요?",
                  "notificationType": "ATTENDANCE"
                }
                """;

        consumer.consumeNotificationRequest(payload, "notification-key-2");

        ArgumentCaptor<SendPushNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(SendPushNotificationRequest.class);
        verify(notificationService).createNotification(requestCaptor.capture(), eq("notification-key-2"));
        verify(fcmSenderService).dispatchPendingDeliveries(100L);

        SendPushNotificationRequest request = requestCaptor.getValue();
        assertThat(request.getNotificationType().name()).isEqualTo("NOTIFICATION_TYPE_ATTENDANCE");
        assertThat(request.hasTargetType()).isFalse();
        assertThat(request.hasTargetId()).isFalse();
    }
}
