package p5laris.eventlog.domain.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;
import p5laris.eventlog.domain.domain.dto.EventLogResponse;
import p5laris.eventlog.domain.domain.entity.EventLog;
import p5laris.eventlog.domain.domain.repository.EventLogRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogService {
    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventLogResponse recordEventLog(EventLogRequest request) {
        if (eventLogRepository.existsByEventId(request.eventId())) {
            log.warn("Duplicate event log received for eventId: {}. Skipping save.", request.eventId());
            return new EventLogResponse(true);
        }

        EventLog eventLog = EventLog.builder()
                .eventId(request.eventId())
                .eventType(request.eventType())
                .sourceService(request.sourceService())
                .userId(request.userId())
                .anonymousId(request.anonymousId())
                .refType(request.refType())
                .refId(request.refId())
                .propertiesJson(parseJson(request.propertiesJson()))
                .contextJson(parseJson(request.contextJson()))
                .occurredAt(request.occurredAt())
                .build();

        eventLogRepository.save(eventLog);

        return new EventLogResponse(true);
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid json", e);
        }
    }

    private LocalDateTime toLocalDateTime(com.google.protobuf.Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(
                timestamp.getSeconds(),
                timestamp.getNanos()
        );

        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
