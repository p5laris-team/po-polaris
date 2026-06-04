package p5laris.eventlog.domain.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(length = 50)
    private String sourceService;

    private Long userId;

    @Column(length = 100)
    private String anonymousId;

    @Column(length = 50)
    private String refType;

    private Long refId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode propertiesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode contextJson;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public EventLog(
            UUID eventId,
            String eventType,
            String sourceService,
            Long userId,
            String anonymousId,
            String refType,
            Long refId,
            JsonNode propertiesJson,
            JsonNode contextJson,
            LocalDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sourceService = sourceService;
        this.userId = userId;
        this.anonymousId = anonymousId;
        this.refType = refType;
        this.refId = refId;
        this.propertiesJson = propertiesJson;
        this.contextJson = contextJson;
        this.occurredAt = occurredAt;
    }
}
