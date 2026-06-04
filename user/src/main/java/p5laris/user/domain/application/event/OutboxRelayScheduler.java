package p5laris.user.domain.application.event;

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
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", outboxEventRepository,
                repo -> repo.countByStatus("PENDING"));
    }

    @Value("${spring.application.name:user}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @GrpcClient("notification")
    private com.p5laris.proto.notification.v1.NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPendingEvents(
                LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE)
        );
        
        for (OutboxEvent pendingEvent : events) {
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

                if ("USER_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    UserEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), UserEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event, outboxEvent.getIdempotencyKey()));
                } else if ("NOTIFICATION_REQUEST".equals(outboxEvent.getAggregateType())) {
                    NotificationRequestEvent event = objectMapper.readValue(outboxEvent.getPayload(), NotificationRequestEvent.class);
                    com.p5laris.proto.notification.v1.SendPushNotificationRequest req = com.p5laris.proto.notification.v1.SendPushNotificationRequest.newBuilder()
                            .setUserId(event.userId())
                            .setTitle(event.title())
                            .setBody(event.body())
                            .setNotificationType(com.p5laris.proto.notification.v1.NotificationType.valueOf(event.notificationType()))
                            .build();
                    notificationStub.sendPushNotification(req);
                }
                
                outboxEvent.success();
                outboxEventRepository.saveAndFlush(outboxEvent);
                log.debug("Successfully relayed outbox event: {}", outboxEvent.getId());

                meterRegistry.counter("outbox.events.processed",
                        "status", "SUCCESS",
                        "aggregate_type", outboxEvent.getAggregateType()
                ).increment();
            } catch (Exception e) {
                log.error("Failed to relay outbox event: {}", pendingEvent.getId(), e);
                
                // 락이 잡힌 최신 엔티티의 attemptCount를 기준으로 갱신
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId()).orElse(pendingEvent);
                int attempt = outboxEvent.getAttemptCount() + 1;
                // 지수 백오프: 1분 -> 2분 -> 4분 -> 8분 ...
                long backoffMinutes = (long) Math.pow(2, attempt - 1);
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(backoffMinutes);
                
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

    private RecordEventLogRequest toRequest(UserEventLogEvent event, String idempotencyKey) throws Exception {
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
