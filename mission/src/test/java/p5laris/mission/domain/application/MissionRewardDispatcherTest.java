package p5laris.mission.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.application.event.MissionNotificationKafkaPublisher;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionRewardDispatcherTest {

    private static final String IDEMPOTENCY_KEY = "MISSION_REWARD:100";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 12, 12, 0);

    @Mock
    private MissionOutboxEventRepository outboxRepository;

    @Mock
    private UserMissionRepository userMissionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private MissionNotificationKafkaPublisher notificationPublisher;

    @Mock
    private MissionRewardBackoffPolicy backoffPolicy;

    @Mock
    private MissionRewardOutboxProperties properties;

    @Mock
    private TransactionTemplate transactionTemplate;

    private MissionRewardDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        dispatcher = new MissionRewardDispatcher(
                outboxRepository,
                userMissionRepository,
                kafkaTemplate,
                notificationPublisher,
                backoffPolicy,
                properties,
                transactionTemplate,
                Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneId.of("Asia/Seoul")),
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("같은 미션 보상 성공 이벤트를 두 번 받아도 보상 완료 알림은 한 번만 보낸다")
    void markSucceeded_duplicateEvent_doesNotDuplicateRewardNotification() {
        UserMission mission = completedMission();
        MissionOutboxEvent outbox = rewardOutbox(mission);

        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));
        when(userMissionRepository.findByIdAndUserIdForUpdate(100L, 1L))
                .thenReturn(Optional.of(mission));

        dispatcher.markSucceeded(200L, IDEMPOTENCY_KEY);
        dispatcher.markSucceeded(200L, IDEMPOTENCY_KEY);

        assertThat(mission.isRewardPaid()).isTrue();
        assertThat(mission.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        verify(notificationPublisher, times(1))
                .sendMissionRewardRecoveredNotification(1L, 100L, 10);
    }

    private UserMission completedMission() {
        UserMission mission = BeanUtils.instantiateClass(UserMission.class);
        ReflectionTestUtils.setField(mission, "id", 100L);
        ReflectionTestUtils.setField(mission, "userId", 1L);
        ReflectionTestUtils.setField(mission, "rewardStarPiece", 10);
        ReflectionTestUtils.setField(mission, "status", UserMissionStatus.COMPLETED);
        return mission;
    }

    private MissionOutboxEvent rewardOutbox(UserMission mission) {
        MissionOutboxEvent outbox = MissionOutboxEvent.rewardRequested(
                mission,
                new ObjectMapper().valueToTree(new MissionRewardRequestedPayload(100L, 1L, 10)),
                IDEMPOTENCY_KEY,
                NOW
        );
        ReflectionTestUtils.setField(outbox, "id", 200L);
        return outbox;
    }
}
