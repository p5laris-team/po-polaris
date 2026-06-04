package p5laris.mission.domain.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;

import java.time.LocalDateTime;

/**
 * mission 모듈에서 외부 모듈 호출 또는 향후 Kafka 발행이 필요한 이벤트를 저장하는 outbox 엔티티다.
 *
 * 모듈별 테이블은 분리하되 aggregate/event/payload 구조는 user/item/character outbox와 맞춰
 * 나중에 브로커 릴레이로 확장하기 쉽게 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_outbox_events")
public class MissionOutboxEvent extends BaseEntity {

    public static final String AGGREGATE_TYPE_MISSION = "MISSION";
    public static final String EVENT_TYPE_MISSION_REWARD_REQUESTED = "MISSION_REWARD_REQUESTED";
    public static final String EVENT_TYPE_MISSION_CHARACTER_EXP_REQUESTED = "MISSION_CHARACTER_EXP_REQUESTED";

    private static final int LAST_ERROR_MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MissionOutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    public static MissionOutboxEvent rewardRequested(
            UserMission mission,
            JsonNode payload,
            String idempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        MissionOutboxEvent event = new MissionOutboxEvent();
        event.aggregateType = AGGREGATE_TYPE_MISSION;
        event.aggregateId = mission.getId();
        event.eventType = EVENT_TYPE_MISSION_REWARD_REQUESTED;
        event.payload = payload;
        event.idempotencyKey = idempotencyKey;
        event.status = MissionOutboxEventStatus.PENDING;
        event.attemptCount = 0;
        event.nextAttemptAt = nextAttemptAt;
        return event;
    }

    public static MissionOutboxEvent characterExpRequested(
            UserMission mission,
            JsonNode payload,
            String idempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        MissionOutboxEvent event = new MissionOutboxEvent();
        event.aggregateType = AGGREGATE_TYPE_MISSION;
        event.aggregateId = mission.getId();
        event.eventType = EVENT_TYPE_MISSION_CHARACTER_EXP_REQUESTED;
        event.payload = payload;
        event.idempotencyKey = idempotencyKey;
        event.status = MissionOutboxEventStatus.PENDING;
        event.attemptCount = 0;
        event.nextAttemptAt = nextAttemptAt;
        return event;
    }

    // PROCESSING 상태의 nextAttemptAt은 "다른 스케줄러가 다시 집을 수 있는 lock 만료 시각"으로 사용한다.
    public void markProcessing(LocalDateTime lockExpiresAt) {
        this.status = MissionOutboxEventStatus.PROCESSING;
        this.nextAttemptAt = lockExpiresAt;
    }

    public void markSucceeded(LocalDateTime succeededAt) {
        this.status = MissionOutboxEventStatus.SUCCEEDED;
        this.nextAttemptAt = succeededAt;
        this.lastErrorMessage = null;
    }

    // 실패 횟수를 올리고 다음 재시도 시각을 기록한다. maxAttempts에 도달하면 자동 재처리 대상에서 제외한다.
    public void recordFailure(String errorMessage, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
        this.status = this.attemptCount >= maxAttempts
                ? MissionOutboxEventStatus.FAILED
                : MissionOutboxEventStatus.PENDING;
    }

    // PENDING 또는 lock이 만료된 PROCESSING row만 스케줄러가 다시 발송할 수 있다.
    public boolean canBeClaimed(LocalDateTime now) {
        return (status == MissionOutboxEventStatus.PENDING || status == MissionOutboxEventStatus.PROCESSING)
                && !nextAttemptAt.isAfter(now);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= LAST_ERROR_MESSAGE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, LAST_ERROR_MESSAGE_MAX_LENGTH);
    }
}
