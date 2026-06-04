package p5laris.character.domain.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "character_outbox_events")
public class CharacterOutboxEvent {

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
    private CharacterOutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static CharacterOutboxEvent pending(
            String aggregateType,
            Long aggregateId,
            String eventType,
            JsonNode payload,
            String idempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        CharacterOutboxEvent event = new CharacterOutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.idempotencyKey = idempotencyKey;
        event.status = CharacterOutboxEventStatus.PENDING;
        event.attemptCount = 0;
        event.nextAttemptAt = nextAttemptAt;
        return event;
    }

    public void markProcessing(LocalDateTime lockExpiresAt) {
        this.status = CharacterOutboxEventStatus.PROCESSING;
        this.nextAttemptAt = lockExpiresAt;
    }

    public void markSucceeded(LocalDateTime succeededAt) {
        this.status = CharacterOutboxEventStatus.SUCCEEDED;
        this.nextAttemptAt = succeededAt;
        this.lastErrorMessage = null;
    }

    public void recordFailure(String errorMessage, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
        this.status = this.attemptCount >= maxAttempts
                ? CharacterOutboxEventStatus.FAILED
                : CharacterOutboxEventStatus.PENDING;
    }

    public boolean canBeClaimed(LocalDateTime now) {
        return (status == CharacterOutboxEventStatus.PENDING || status == CharacterOutboxEventStatus.PROCESSING)
                && !nextAttemptAt.isAfter(now);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
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
