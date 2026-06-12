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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.application.event.MissionNotificationKafkaPublisher;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(properties.getProcessingTimeoutSeconds()).thenReturn(30L);
        lenient().when(properties.getMaxAttempts()).thenReturn(3);
        lenient().when(backoffPolicy.nextAttemptAt(any(), any(Integer.class)))
                .thenAnswer(invocation -> invocation.<LocalDateTime>getArgument(0).plusMinutes(1));

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

    @Test
    @DisplayName("즉시 발행은 READY outbox를 PROCESSING으로 선점하고 Kafka 이벤트를 보낸다")
    void dispatchNow_pendingOutbox_publishesRewardEvent() {
        MissionOutboxEvent outbox = rewardOutbox(completedMission());
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));
        when(kafkaTemplate.send(eq("star-piece-earn-requested"), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchNow(200L);

        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PROCESSING);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        verify(kafkaTemplate).send(eq("star-piece-earn-requested"), eq(IDEMPOTENCY_KEY), any());
    }

    @Test
    @DisplayName("존재하지 않는 즉시 발행 요청은 보상 실패로 처리한다")
    void dispatchNow_missingOutbox_throwsRewardFailure() {
        when(outboxRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatcher.dispatchNow(999L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REWARD_FAILED);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("스케줄러는 선점 가능한 outbox만 발행하고 성공 개수를 반환한다")
    void dispatchDue_skipsUnavailableOutboxAndCountsSuccess() {
        MissionOutboxEvent due = rewardOutbox(completedMission());
        MissionOutboxEvent future = rewardOutbox(completedMission());
        ReflectionTestUtils.setField(future, "id", 201L);
        ReflectionTestUtils.setField(future, "nextAttemptAt", NOW.plusMinutes(10));

        when(outboxRepository.findDispatchableIds(
                any(), any(), any(), eq(NOW), any()
        )).thenReturn(List.of(200L, 201L));
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(due));
        when(outboxRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(future));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        int count = dispatcher.dispatchDue(0);

        assertThat(count).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    @DisplayName("Kafka 발행 예외는 outbox 실패 횟수와 다음 재시도 시각을 기록한다")
    void dispatchDue_kafkaFailure_recordsBackoffAndContinues() {
        MissionOutboxEvent outbox = rewardOutbox(completedMission());
        when(outboxRepository.findDispatchableIds(any(), any(), any(), eq(NOW), any()))
                .thenReturn(List.of(200L));
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        int count = dispatcher.dispatchDue(10);

        assertThat(count).isZero();
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("깨진 payload는 batch를 중단하지 않고 poison event로 실패 기록한다")
    void dispatchDue_invalidPayload_recordsPoisonFailure() {
        MissionOutboxEvent outbox = rewardOutbox(completedMission());
        ReflectionTestUtils.setField(
                outbox,
                "payload",
                new ObjectMapper().valueToTree(new MissionRewardRequestedPayload(999L, 1L, 10))
        );
        when(outboxRepository.findDispatchableIds(any(), any(), any(), eq(NOW), any()))
                .thenReturn(List.of(200L));
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));

        int count = dispatcher.dispatchDue(10);

        assertThat(count).isZero();
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("최대 재시도 횟수에 도달하면 outbox를 FAILED로 고정한다")
    void markFailed_reachesMaxAttempts_marksPermanentlyFailed() {
        MissionOutboxEvent outbox = rewardOutbox(completedMission());
        ReflectionTestUtils.setField(outbox, "attemptCount", 2);
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));

        dispatcher.markFailed(200L, IDEMPOTENCY_KEY, "wallet timeout");

        assertThat(outbox.getAttemptCount()).isEqualTo(3);
        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.FAILED);
        assertThat(outbox.getLastErrorMessage()).isEqualTo("wallet timeout");
    }

    @Test
    @DisplayName("성공 이벤트의 멱등키가 다르면 보상 상태를 변경하지 않는다")
    void markSucceeded_wrongIdempotencyKey_rejectsEvent() {
        MissionOutboxEvent outbox = rewardOutbox(completedMission());
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));

        assertThatThrownBy(() -> dispatcher.markSucceeded(200L, "wrong-key"))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REWARD_FAILED);

        verify(userMissionRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    @DisplayName("완료되지 않은 미션은 보상 성공 상태로 확정할 수 없다")
    void markSucceeded_incompleteMission_rejectsTransition() {
        UserMission mission = completedMission();
        ReflectionTestUtils.setField(mission, "status", UserMissionStatus.ANSWERING);
        MissionOutboxEvent outbox = rewardOutbox(mission);
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));
        when(userMissionRepository.findByIdAndUserIdForUpdate(100L, 1L))
                .thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> dispatcher.markSucceeded(200L, IDEMPOTENCY_KEY))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_INVALID_STATUS);

        assertThat(outbox.getStatus()).isNotEqualTo(MissionOutboxEventStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("보상 복구 알림 실패는 성공 확정을 롤백하지 않는다")
    void markSucceeded_notificationFailure_keepsRewardSucceeded() {
        UserMission mission = completedMission();
        MissionOutboxEvent outbox = rewardOutbox(mission);
        when(outboxRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(outbox));
        when(userMissionRepository.findByIdAndUserIdForUpdate(100L, 1L))
                .thenReturn(Optional.of(mission));
        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationPublisher)
                .sendMissionRewardRecoveredNotification(1L, 100L, 10);

        dispatcher.markSucceeded(200L, IDEMPOTENCY_KEY);

        assertThat(mission.isRewardPaid()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
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
