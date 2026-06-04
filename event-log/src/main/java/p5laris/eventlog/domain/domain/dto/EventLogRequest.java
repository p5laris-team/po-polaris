package p5laris.eventlog.domain.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventLogRequest(
        UUID eventId,
        String eventType,
        String sourceService,
        Long userId,
        String anonymousId,
        String refType,
        Long refId,
        String propertiesJson,
        String contextJson,
        LocalDateTime occurredAt
) {
}