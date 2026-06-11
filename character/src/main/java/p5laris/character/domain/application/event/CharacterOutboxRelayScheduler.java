package p5laris.character.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.ShareRewardBackoffPolicy;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterOutboxRelayScheduler {

    private static final String CHARACTER_EVENT_LOG_TOPIC = "character-event-logs";
    private static final String NOTIFICATION_REQUEST_TOPIC = "notification-requests";
    private static final int BATCH_SIZE = 100;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final CharacterOutboxEventRepository characterOutboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ShareRewardBackoffPolicy backoffPolicy;
    private final ShareRewardOutboxProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("character.outbox.pending.count", characterOutboxEventRepository,
                repo -> repo.countByStatus(CharacterOutboxEventStatus.PENDING));
    }

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void relayEvents() {
        relayByAggregateType(CharacterEventLogEventListener.AGGREGATE_TYPE_CHARACTER_EVENT_LOG);
        relayByAggregateType(ShareEventLogEventListener.AGGREGATE_TYPE_SHARE_EVENT_LOG);
        relayByAggregateType(CharacterNotificationRequestPublisher.AGGREGATE_TYPE_NOTIFICATION_REQUEST);
    }

    private void relayByAggregateType(String aggregateType) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> outboxIds = characterOutboxEventRepository.findDispatchableIdsByAggregateType(
                aggregateType,
                CharacterOutboxEventStatus.PENDING,
                CharacterOutboxEventStatus.PROCESSING,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long outboxId : outboxIds) {
            relayOne(outboxId);
        }
    }

    private void relayOne(Long outboxId) {
        CharacterOutboxEvent outbox = null;
        try {
            outbox = characterOutboxEventRepository.findByIdForUpdate(outboxId).orElse(null);
            if (outbox == null || !outbox.canBeClaimed(LocalDateTime.now(clock))) {
                return;
            }

            outbox.markProcessing(LocalDateTime.now(clock).plusSeconds(properties.getProcessingTimeoutSeconds()));
            characterOutboxEventRepository.saveAndFlush(outbox);

            publish(outbox);

            outbox.markSucceeded(LocalDateTime.now(clock));
            characterOutboxEventRepository.saveAndFlush(outbox);
            meterRegistry.counter("character.outbox.events.processed",
                    "status", "SUCCESS",
                    "aggregate_type", outbox.getAggregateType()
            ).increment();
        } catch (Exception e) {
            Long id = outbox != null ? outbox.getId() : outboxId;
            String aggregateType = outbox != null ? outbox.getAggregateType() : "UNKNOWN";
            log.error("캐릭터 outbox Kafka relay 실패. id={}, aggregateType={}", id, aggregateType, e);

            CharacterOutboxEvent latest = characterOutboxEventRepository.findByIdForUpdate(outboxId).orElse(outbox);
            if (latest != null) {
                int nextAttemptCount = latest.getAttemptCount() + 1;
                latest.recordFailure(
                        e.getMessage(),
                        backoffPolicy.nextAttemptAt(LocalDateTime.now(clock), nextAttemptCount),
                        properties.getMaxAttempts()
                );
                characterOutboxEventRepository.saveAndFlush(latest);
            }

            meterRegistry.counter("character.outbox.events.processed",
                    "status", "FAILURE",
                    "aggregate_type", aggregateType
            ).increment();
        }
    }

    private void publish(CharacterOutboxEvent outbox) throws Exception {
        if (CharacterEventLogEventListener.AGGREGATE_TYPE_CHARACTER_EVENT_LOG.equals(outbox.getAggregateType())) {
            CharacterEventLogEvent event = objectMapper.treeToValue(outbox.getPayload(), CharacterEventLogEvent.class);
            sendAndWait(CHARACTER_EVENT_LOG_TOPIC, outbox.getIdempotencyKey(), event);
            return;
        }

        if (ShareEventLogEventListener.AGGREGATE_TYPE_SHARE_EVENT_LOG.equals(outbox.getAggregateType())) {
            ShareEventLogEvent event = objectMapper.treeToValue(outbox.getPayload(), ShareEventLogEvent.class);
            sendAndWait(CHARACTER_EVENT_LOG_TOPIC, outbox.getIdempotencyKey(), event);
            return;
        }

        if (CharacterNotificationRequestPublisher.AGGREGATE_TYPE_NOTIFICATION_REQUEST.equals(outbox.getAggregateType())) {
            NotificationRequestEvent event = objectMapper.treeToValue(outbox.getPayload(), NotificationRequestEvent.class);
            sendAndWait(NOTIFICATION_REQUEST_TOPIC, outbox.getIdempotencyKey(), event);
            return;
        }

        throw new IllegalStateException("Unsupported character outbox aggregateType: " + outbox.getAggregateType());
    }

    private void sendAndWait(String topic, String key, Object event) throws Exception {
        kafkaTemplate.send(topic, key, event).get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
