package p5laris.mission.domain.application.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.mission.domain.infrastructure.grpc.NotificationPushClient;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionNotificationEventListenerTest {

    @Mock
    private NotificationPushClient notificationPushClient;

    private MissionNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MissionNotificationEventListener(notificationPushClient);
    }

    @Test
    void 미션_제안_이벤트면_알림_전송을_요청한다() {
        MissionEventLogEvent event = event(
                "MISSION_OFFERED",
                1L,
                10L,
                Map.of("missionTitle", "물 한 잔 마시기")
        );

        listener.handle(event);

        verify(notificationPushClient).sendMissionOfferNotification(
                1L,
                10L,
                "물 한 잔 마시기"
        );
    }

    @Test
    void 미션_제안이_아닌_이벤트는_알림을_보내지_않는다() {
        MissionEventLogEvent event = event(
                "MISSION_COMPLETED",
                1L,
                10L,
                Map.of("missionTitle", "물 한 잔 마시기")
        );

        listener.handle(event);

        verify(notificationPushClient, never()).sendMissionOfferNotification(
                1L,
                10L,
                "물 한 잔 마시기"
        );
    }

    @Test
    void 알림_전송이_실패해도_미션_이벤트_처리는_실패시키지_않는다() {
        MissionEventLogEvent event = event(
                "MISSION_OFFERED",
                1L,
                10L,
                Map.of("missionTitle", "물 한 잔 마시기")
        );

        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationPushClient)
                .sendMissionOfferNotification(1L, 10L, "물 한 잔 마시기");

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void 사용자나_미션_id가_없으면_알림을_보내지_않는다() {
        MissionEventLogEvent event = event(
                "MISSION_OFFERED",
                null,
                10L,
                Map.of("missionTitle", "물 한 잔 마시기")
        );

        listener.handle(event);

        verify(notificationPushClient, never()).sendMissionOfferNotification(
                null,
                10L,
                "물 한 잔 마시기"
        );
    }

    private MissionEventLogEvent event(
            String eventType,
            Long userId,
            Long missionId,
            Map<String, Object> metadata
    ) {
        return new MissionEventLogEvent(
                eventType,
                userId,
                "MISSION",
                missionId,
                metadata,
                OffsetDateTime.now()
        );
    }
}
