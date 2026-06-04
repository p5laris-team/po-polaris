package p5laris.item.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
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

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", outboxEventRepository,
                repo -> repo.countByStatus("PENDING"));
    }

    @Value("${spring.application.name:item}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

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
                // 비관적 락 획득 및 최신 상태 조회
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId())
                        .orElse(null);

                if (outboxEvent == null) {
                    continue;
                }

                // 최신 상태 재검증 (이미 다른 서버가 발송했거나 발송 중인 경우 건너뜀)
                if (!"PENDING".equals(outboxEvent.getStatus())) {
                    continue;
                }

                // 재시도 대기 시각이 아직 지나지 않은 경우 건너뜀 (타 서버가 처리 실패 후 대기 중인 경우)
                if (outboxEvent.getNextAttemptAt() != null && outboxEvent.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                    continue;
                }

                // 상태를 PROCESSING으로 변경하여 락 효과
                outboxEvent.processing();
                outboxEventRepository.saveAndFlush(outboxEvent);

                if ("ITEM_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    ItemEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), ItemEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event, outboxEvent.getIdempotencyKey()));
                }
                
                outboxEvent.success();
                outboxEventRepository.saveAndFlush(outboxEvent);
                log.debug("Outbox event succeeded. id={}", outboxEvent.getId());

                meterRegistry.counter("outbox.events.processed",
                        "status", "SUCCESS",
                        "aggregate_type", outboxEvent.getAggregateType()
                ).increment();
                
            } catch (Exception e) {
                log.error("Outbox event failed. id={}, type={}", pendingEvent.getId(), pendingEvent.getAggregateType(), e);
                
                // 락이 잡힌 최신 엔티티의 attemptCount를 기준으로 갱신
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

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    public void cleanupSucceededEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        outboxEventRepository.deleteSucceededEvents(threshold);
        log.info("Cleaned up succeeded outbox events older than 1 day");
    }

    private RecordEventLogRequest toRequest(ItemEventLogEvent event, String idempotencyKey) throws JsonProcessingException {
        RecordEventLogRequest.Builder builder = RecordEventLogRequest.newBuilder()
                .setEventId(idempotencyKey)
                .setEventType(event.eventType())
                .setSourceService(sourceService)
                .setOccurredAt(toTimestamp(event.occurredAt().toInstant()));

        if (event.userId() != null) {
            builder.setUserId(event.userId());
        }
        if (event.refType() != null && !event.refType().isBlank()) {
            builder.setRefType(event.refType());
        }
        if (event.refId() != null) {
            builder.setRefId(event.refId());
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            builder.setPropertiesJson(objectMapper.writeValueAsString(event.metadata()));
        }

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
