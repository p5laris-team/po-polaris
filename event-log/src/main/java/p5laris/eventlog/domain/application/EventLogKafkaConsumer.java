package p5laris.eventlog.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event-log 모듈의 Kafka 메시지 컨슈머 클래스입니다.
 * 
 * [역할]
 * 1. 'user-event-logs' 및 'item-event-logs' 토픽을 구독하여 각 모듈에서 비동기로 넘어오는 이벤트를 수신합니다.
 * 2. 수신한 JSON 메시지에서 비즈니스 속성을 추출하여 공통 EventLogRequest DTO를 생성합니다.
 * 3. 멱등키(Kafka Message Key)를 기반으로 중복 저장을 방어하면서 EventLogService를 통해 DB에 로그를 최종 적재합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogKafkaConsumer {

    private final EventLogService eventLogService;
    private final ObjectMapper objectMapper;

    /**
     * 'user-event-logs' 및 'item-event-logs' 토픽으로부터 유입되는 메시지를 처리하는 리스너 메서드입니다.
     *
     * @param messagePayload JSON 포맷의 이벤트 정보 문자열
     * @param idempotencyKey 중복 차단용 멱등키 (Kafka Message Key)
     * @param topic 메시지가 인입된 카프카 토픽명
     */
    @KafkaListener(topics = {"user-event-logs", "item-event-logs"}, groupId = "event-log-group")
    public void consumeEventLog(
            String messagePayload, 
            @Header(KafkaHeaders.RECEIVED_KEY) String idempotencyKey,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.info("[Kafka Consumer] 이벤트 로그 수신 - 토픽: {}, 멱등키(Key): {}", topic, idempotencyKey);
        
        try {
            // 1. JSON 형태의 원시 메시지를 공통 Map 구조로 역직렬화
            @SuppressWarnings("unchecked")
            Map<String, Object> rawEvent = objectMapper.readValue(messagePayload, Map.class);

            // 2. 이벤트 비즈니스 필드 추출 및 타입 캐스팅
            String eventType = (String) rawEvent.get("eventType");
            
            Number userIdNum = (Number) rawEvent.get("userId");
            Long userId = userIdNum != null ? userIdNum.longValue() : null;
            
            String refType = (String) rawEvent.get("refType");
            
            Number refIdNum = (Number) rawEvent.get("refId");
            Long refId = refIdNum != null ? refIdNum.longValue() : null;
            
            // metadata 맵 정보는 JSON String 포맷으로 재직렬화하여 DB 컬럼에 삽입 준비
            Object rawMetadata = rawEvent.get("metadata");
            String propertiesJson = rawMetadata != null ? objectMapper.writeValueAsString(rawMetadata) : null;
            
            // 3. 발생 시각(occurredAt) 파싱 처리
            String occurredAtStr = (String) rawEvent.get("occurredAt");
            LocalDateTime occurredAt = occurredAtStr != null 
                    ? OffsetDateTime.parse(occurredAtStr).toLocalDateTime() 
                    : LocalDateTime.now();

            // 4. 서비스 레이어 호출을 위한 EventLogRequest DTO 빌드
            // idempotencyKey를 UUID 포맷의 eventId로 전환
            EventLogRequest request = new EventLogRequest(
                    UUID.fromString(idempotencyKey),
                    eventType,
                    topic.contains("user") ? "user" : "item", // 토픽명을 식별하여 소스 서비스 주입
                    userId,
                    null,              // anonymousId
                    refType,
                    refId,
                    propertiesJson,
                    null,              // contextJson
                    occurredAt
            );

            // 5. 비즈니스 서비스 호출 (내부적으로 existByEventId를 통해 중복 저장 방지함)
            eventLogService.recordEventLog(request);
            log.info("[Kafka Consumer] 이벤트 로그 적재 처리 완료 - 멱등키: {}", idempotencyKey);
            
        } catch (Exception e) {
            log.error("[Kafka Consumer] 이벤트 로그 처리 실패 - 토픽: {}, 멱등키: {}", topic, idempotencyKey, e);
            // 에러를 던지지 않고 catch하여 로그를 찍고 처리 성공/실패 여부를 모니터링함
            // (컨슈머 롤백 시 메시지가 무한 재처리되는 브로커 병목을 예방하기 위함)
        }
    }
}
