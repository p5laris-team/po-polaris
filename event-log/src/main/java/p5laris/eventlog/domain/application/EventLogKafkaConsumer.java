package p5laris.eventlog.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogKafkaConsumer {

    private final EventLogService eventLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    "user-event-logs",
                    "item-event-logs",
                    "character-event-logs",
                    "mission-event-logs",
                    "ai-event-logs"
            },
            groupId = "event-log-group"
    )
    public void consumeEventLog(
            String messagePayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String idempotencyKey,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.info("[Kafka Consumer] 이벤트 로그 수신 - topic={}, key={}", topic, idempotencyKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawEvent = objectMapper.readValue(messagePayload, Map.class);

            String eventType = (String) rawEvent.get("eventType");
            Number userIdNum = (Number) rawEvent.get("userId");
            Long userId = userIdNum != null ? userIdNum.longValue() : null;
            String refType = (String) rawEvent.get("refType");
            Number refIdNum = (Number) rawEvent.get("refId");
            Long refId = refIdNum != null ? refIdNum.longValue() : null;

            Object rawProperties = rawEvent.get("metadata");
            if (rawProperties == null) {
                rawProperties = rawEvent.get("properties");
            }
            String propertiesJson = rawProperties != null ? objectMapper.writeValueAsString(rawProperties) : null;

            String occurredAtStr = (String) rawEvent.get("occurredAt");
            LocalDateTime occurredAt = occurredAtStr != null
                    ? OffsetDateTime.parse(occurredAtStr).toLocalDateTime()
                    : LocalDateTime.now();

            EventLogRequest request = new EventLogRequest(
                    toEventId(idempotencyKey),
                    eventType,
                    resolveSourceService(topic),
                    userId,
                    null,
                    refType,
                    refId,
                    propertiesJson,
                    null,
                    occurredAt
            );

            eventLogService.recordEventLog(request);
            log.info("[Kafka Consumer] 이벤트 로그 적재 완료 - key={}", idempotencyKey);
        } catch (Exception e) {
            log.error("[Kafka Consumer] 이벤트 로그 처리 실패 - topic={}, key={}", topic, idempotencyKey, e);
            throw new IllegalStateException("이벤트 로그 메시지 처리에 실패했습니다.", e);
        }
    }

    private UUID toEventId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String resolveSourceService(String topic) {
        if (topic == null || topic.isBlank()) {
            return "unknown";
        }
        int suffixIndex = topic.indexOf("-event-logs");
        if (suffixIndex > 0) {
            return topic.substring(0, suffixIndex);
        }
        return topic;
    }
}
