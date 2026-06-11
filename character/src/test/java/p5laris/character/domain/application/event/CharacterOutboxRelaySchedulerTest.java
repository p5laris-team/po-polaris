package p5laris.character.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.character.domain.application.ShareRewardBackoffPolicy;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterOutboxRelaySchedulerTest {

    @Mock
    private CharacterOutboxEventRepository characterOutboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ObjectMapper objectMapper;
    private Clock clock;
    private CharacterOutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        clock = Clock.fixed(Instant.parse("2026-06-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));

        ShareRewardOutboxProperties properties = new ShareRewardOutboxProperties();
        properties.setMaxAttempts(5);
        properties.setProcessingTimeoutSeconds(30);
        properties.setRetryInitialDelaySeconds(1);
        properties.setRetryMaxDelaySeconds(60);

        scheduler = new CharacterOutboxRelayScheduler(
                characterOutboxEventRepository,
                objectMapper,
                kafkaTemplate,
                new ShareRewardBackoffPolicy(properties),
                properties,
                clock,
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("Kafka 발행 ack가 성공해야 character outbox를 SUCCEEDED로 확정한다")
    void relayEvents_kafka_send_success_marksSucceeded() {
        CharacterOutboxEvent event = pendingCharacterEvent(1L);
        stubDispatchableCharacterEvent(event);
        when(kafkaTemplate.send(eq("character-event-logs"), eq("character-key-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.relayEvents();

        assertThat(event.getStatus()).isEqualTo(CharacterOutboxEventStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Kafka 발행 ack가 실패하면 character outbox를 재시도 대상으로 남긴다")
    void relayEvents_kafka_send_failure_marksRetryable() {
        CharacterOutboxEvent event = pendingCharacterEvent(1L);
        stubDispatchableCharacterEvent(event);
        when(kafkaTemplate.send(eq("character-event-logs"), eq("character-key-1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        scheduler.relayEvents();

        assertThat(event.getStatus()).isEqualTo(CharacterOutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastErrorMessage()).contains("broker unavailable");
    }

    private void stubDispatchableCharacterEvent(CharacterOutboxEvent event) {
        when(characterOutboxEventRepository.findDispatchableIdsByAggregateType(
                eq(CharacterEventLogEventListener.AGGREGATE_TYPE_CHARACTER_EVENT_LOG),
                eq(CharacterOutboxEventStatus.PENDING),
                eq(CharacterOutboxEventStatus.PROCESSING),
                any(),
                any()
        )).thenReturn(List.of(event.getId()));
        when(characterOutboxEventRepository.findDispatchableIdsByAggregateType(
                eq(ShareEventLogEventListener.AGGREGATE_TYPE_SHARE_EVENT_LOG),
                eq(CharacterOutboxEventStatus.PENDING),
                eq(CharacterOutboxEventStatus.PROCESSING),
                any(),
                any()
        )).thenReturn(List.of());
        when(characterOutboxEventRepository.findDispatchableIdsByAggregateType(
                eq(CharacterNotificationRequestPublisher.AGGREGATE_TYPE_NOTIFICATION_REQUEST),
                eq(CharacterOutboxEventStatus.PENDING),
                eq(CharacterOutboxEventStatus.PROCESSING),
                any(),
                any()
        )).thenReturn(List.of());
        when(characterOutboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    }

    private CharacterOutboxEvent pendingCharacterEvent(Long id) {
        CharacterEventLogEvent payload = new CharacterEventLogEvent(
                "CHARACTER_CREATED",
                1L,
                "CHARACTER",
                100L,
                Map.of("name", "Nova"),
                OffsetDateTime.now(clock)
        );
        CharacterOutboxEvent event = CharacterOutboxEvent.pending(
                CharacterEventLogEventListener.AGGREGATE_TYPE_CHARACTER_EVENT_LOG,
                100L,
                "CHARACTER_CREATED",
                objectMapper.valueToTree(payload),
                "character-key-" + id,
                LocalDateTime.now(clock).minusSeconds(1)
        );
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
