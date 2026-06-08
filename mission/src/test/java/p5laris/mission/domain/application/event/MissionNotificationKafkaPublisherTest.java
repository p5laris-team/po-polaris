package p5laris.mission.domain.application.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionNotificationKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MissionNotificationKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MissionNotificationKafkaPublisher(kafkaTemplate);
    }

    @Test
    void 미션_제안_알림은_미션_target을_포함해_Kafka로_발행한다() {
        publisher.sendMissionOfferNotification(1001L, 2001L, "물 한 잔 마시기");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification-requests"), anyString(), payloadCaptor.capture());

        MissionNotificationRequestEvent payload = (MissionNotificationRequestEvent) payloadCaptor.getValue();
        assertThat(payload.userId()).isEqualTo(1001L);
        assertThat(payload.title()).isEqualTo("새 미션이 도착했어요");
        assertThat(payload.body()).isEqualTo("물 한 잔 마시기 미션을 해볼까요?");
        assertThat(payload.notificationType()).isEqualTo("MISSION");
        assertThat(payload.targetType()).isEqualTo("MISSION");
        assertThat(payload.targetId()).isEqualTo(2001L);
    }

    @Test
    void Kafka_발행이_실패해도_예외를_전파하지_않는다() {
        when(kafkaTemplate.send(eq("notification-requests"), anyString(), any()))
                .thenThrow(new RuntimeException("kafka unavailable"));

        assertThatCode(() -> publisher.sendMissionRewardRecoveredNotification(1001L, 2001L, 10))
                .doesNotThrowAnyException();
    }
}
