package p5laris.item.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemOutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // gRPC Blocking Stub 대신 비동기 카프카 메시지 전송을 위한 템플릿을 주입받습니다.
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", outboxEventRepository,
                repo -> repo.countByStatus("PENDING"));
    }

    @Value("${spring.application.name:item}")
    private String sourceService;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 주기적으로 (5초 간격) DB의 아웃박스 테이블에서 전송 대기 상태(PENDING)인 이벤트를 조회하여
     * 카프카 브로커로 비동기 전송(릴레이)을 수행합니다.
     */
    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(
                LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent pendingEvent : pendingEvents) {
            try {
                // 비관적 락(Lock)을 획득하여 다중 서버 환경에서 동일한 이벤트를 중복 처리하지 않도록 방어
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId())
                        .orElse(null);

                if (outboxEvent == null) {
                    continue;
                }

                // 처리 대기 상태가 아니면 다른 서버에서 이미 처리 중인 것이므로 스킵
                if (!"PENDING".equals(outboxEvent.getStatus())) {
                    continue;
                }

                // 백오프 시간에 걸려있는 경우 (실패 후 재시도 대기 시간) 스킵
                if (outboxEvent.getNextAttemptAt() != null && outboxEvent.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                    continue;
                }

                // 상태를 PROCESSING으로 올려 락 세팅
                outboxEvent.processing();
                outboxEventRepository.saveAndFlush(outboxEvent);

                // 이벤트 로그 타입인 경우, payload를 역직렬화하여 카프카 토픽 'item-event-logs'로 전송
                if ("ITEM_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    ItemEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), ItemEventLogEvent.class);
                    
                    // [Kafka 도입] gRPC 동기 호출 대신 Kafka 토픽 발행으로 전격 비동기화
                    kafkaTemplate.send("item-event-logs", outboxEvent.getIdempotencyKey(), event);
                }
                
                // 전송 성공 처리
                outboxEvent.success();
                outboxEventRepository.saveAndFlush(outboxEvent);
                log.debug("Successfully relayed item outbox event via Kafka. id={}", outboxEvent.getId());

                meterRegistry.counter("outbox.events.processed",
                        "status", "SUCCESS",
                        "aggregate_type", outboxEvent.getAggregateType()
                ).increment();
                
            } catch (Exception e) {
                log.error("Failed to relay item outbox event. id={}, type={}", pendingEvent.getId(), pendingEvent.getAggregateType(), e);
                
                // 락이 잡힌 최신 엔티티의 attemptCount를 기준으로 재시도 횟수를 늘리고 지수 백오프 적용
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId()).orElse(pendingEvent);
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes((long) Math.pow(2, outboxEvent.getAttemptCount()));
                outboxEvent.fail(e.getMessage(), nextAttempt, MAX_ATTEMPTS);
                outboxEventRepository.saveAndFlush(outboxEvent);

                meterRegistry.counter("outbox.events.processed",
                        "status", "FAILURE",
                        "aggregate_type", pendingEvent.getAggregateType()
                ).increment();
            }
        }
    }

    /**
     * 성공적으로 발송 완료된(SUCCEEDED) 1일 이상 지난 아웃박스 데이터를 매일 새벽 2시에 일괄 정리(Delete)합니다.
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    public void cleanupSucceededEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        outboxEventRepository.deleteSucceededEvents(threshold);
        log.info("Cleaned up succeeded outbox events older than 1 day");
    }
}
