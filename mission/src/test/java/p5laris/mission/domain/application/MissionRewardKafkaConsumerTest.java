package p5laris.mission.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.mission.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.mission.domain.application.event.StarPieceEarnedEvent;
import p5laris.mission.domain.exception.KafkaConsumerProcessingException;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionRewardKafkaConsumerTest {

    private static final Long OUTBOX_ID = 200L;
    private static final String IDEMPOTENCY_KEY = "MISSION_REWARD:100";

    @Mock
    private MissionRewardDispatcher missionRewardDispatcher;

    private MissionRewardKafkaConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new MissionRewardKafkaConsumer(missionRewardDispatcher, objectMapper);
    }

    @Test
    void earnedMissionReward_marksOutboxSucceeded() throws JsonProcessingException {
        consumer.handleStarPieceEarned(objectMapper.writeValueAsString(earnedEvent(
                "MISSION_REWARD",
                "MISSION"
        )));

        verify(missionRewardDispatcher).markSucceeded(OUTBOX_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void earnedEventForAnotherReason_isIgnored() throws JsonProcessingException {
        consumer.handleStarPieceEarned(objectMapper.writeValueAsString(earnedEvent(
                "PURCHASE_REFUND",
                "MISSION"
        )));

        verify(missionRewardDispatcher, never()).markSucceeded(OUTBOX_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void earnedEventForAnotherRefType_isIgnored() throws JsonProcessingException {
        consumer.handleStarPieceEarned(objectMapper.writeValueAsString(earnedEvent(
                "MISSION_REWARD",
                "ITEM"
        )));

        verify(missionRewardDispatcher, never()).markSucceeded(OUTBOX_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void malformedEarnedPayload_throwsRetryableProcessingException() {
        assertThatThrownBy(() -> consumer.handleStarPieceEarned("{not-json"))
                .isInstanceOf(KafkaConsumerProcessingException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(missionRewardDispatcher, never()).markSucceeded(OUTBOX_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void failedMissionReward_recordsFailureReason() throws JsonProcessingException {
        consumer.handleStarPieceEarnFailed(objectMapper.writeValueAsString(failedEvent(
                "MISSION_REWARD",
                "MISSION"
        )));

        verify(missionRewardDispatcher)
                .markFailed(OUTBOX_ID, IDEMPOTENCY_KEY, "WALLET_UNAVAILABLE");
    }

    @Test
    void failedEventForAnotherDomain_isIgnored() throws JsonProcessingException {
        consumer.handleStarPieceEarnFailed(objectMapper.writeValueAsString(failedEvent(
                "MISSION_REWARD",
                "CHARACTER"
        )));

        verify(missionRewardDispatcher, never())
                .markFailed(OUTBOX_ID, IDEMPOTENCY_KEY, "WALLET_UNAVAILABLE");
    }

    @Test
    void nullFailedPayload_throwsRetryableProcessingException() {
        assertThatThrownBy(() -> consumer.handleStarPieceEarnFailed(null))
                .isInstanceOf(KafkaConsumerProcessingException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(missionRewardDispatcher, never())
                .markFailed(OUTBOX_ID, IDEMPOTENCY_KEY, "WALLET_UNAVAILABLE");
    }

    @Test
    void dispatcherDomainException_doesNotFailKafkaListener() throws JsonProcessingException {
        doThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .when(missionRewardDispatcher)
                .markSucceeded(OUTBOX_ID, IDEMPOTENCY_KEY);

        assertThatCode(() -> consumer.handleStarPieceEarned(objectMapper.writeValueAsString(
                earnedEvent("MISSION_REWARD", "MISSION")
        ))).doesNotThrowAnyException();
    }

    @Test
    void failedResultDomainException_doesNotFailKafkaListener() throws JsonProcessingException {
        doThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .when(missionRewardDispatcher)
                .markFailed(OUTBOX_ID, IDEMPOTENCY_KEY, "WALLET_UNAVAILABLE");

        assertThatCode(() -> consumer.handleStarPieceEarnFailed(objectMapper.writeValueAsString(
                failedEvent("MISSION_REWARD", "MISSION")
        ))).doesNotThrowAnyException();
    }

    private StarPieceEarnedEvent earnedEvent(String reason, String refType) {
        return StarPieceEarnedEvent.builder()
                .outboxId(OUTBOX_ID)
                .userId(1L)
                .amount(10)
                .reason(reason)
                .refType(refType)
                .refId(100L)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .balanceAfter(30)
                .transactionId(300L)
                .build();
    }

    private StarPieceEarnFailedEvent failedEvent(String reason, String refType) {
        return StarPieceEarnFailedEvent.builder()
                .outboxId(OUTBOX_ID)
                .userId(1L)
                .amount(10)
                .reason(reason)
                .refType(refType)
                .refId(100L)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .errorCode("WALLET_UNAVAILABLE")
                .build();
    }
}
