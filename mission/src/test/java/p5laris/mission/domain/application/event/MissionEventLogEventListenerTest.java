package p5laris.mission.domain.application.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionEventLogEventListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MissionEventLogEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MissionEventLogEventListener(kafkaTemplate);
    }

    @Test
    void 미션_이벤트를_Kafka_토픽으로_발행한다() {
        MissionEventLogEvent event = event();

        listener.handle(event);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("mission-event-logs"), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isSameAs(event);
    }

    @Test
    void Kafka_발행이_실패해도_미션_흐름으로_예외를_전파하지_않는다() {
        when(kafkaTemplate.send(eq("mission-event-logs"), anyString(), any()))
                .thenThrow(new RuntimeException("kafka unavailable"));

        assertThatCode(() -> listener.handle(event())).doesNotThrowAnyException();
    }

    private MissionEventLogEvent event() {
        return new MissionEventLogEvent(
                "MISSION_COMPLETED",
                1001L,
                "MISSION",
                2001L,
                Map.of("rewardStarPiece", 10),
                OffsetDateTime.parse("2026-05-21T10:15:30+09:00")
        );
    }
}
