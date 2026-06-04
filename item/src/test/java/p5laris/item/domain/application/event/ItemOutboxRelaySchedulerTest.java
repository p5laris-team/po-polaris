package p5laris.item.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemOutboxRelaySchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    private SimpleMeterRegistry meterRegistry;
    private ItemOutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        scheduler = new ItemOutboxRelayScheduler(
                outboxEventRepository,
                objectMapper,
                meterRegistry
        );
        ReflectionTestUtils.setField(scheduler, "eventLogStub", eventLogStub);
        ReflectionTestUtils.setField(scheduler, "sourceService", "item");
        scheduler.init();
    }

    @Test
    void gauge_pending_count_is_registered_correctly() {
        when(outboxEventRepository.countByStatus("PENDING")).thenReturn(5L);

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isEqualTo(5.0);
    }

    @Test
    void processOutboxEvents_success_increments_counter() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .aggregateType("ITEM_EVENT_LOG")
                .aggregateId(100L)
                .eventType("ITEM_PURCHASED")
                .payload("{\"eventType\":\"ITEM_PURCHASED\",\"userId\":1,\"refType\":\"ITEM\",\"refId\":10,\"metadata\":{},\"occurredAt\":\"2026-06-02T14:10:00+09:00\"}")
                .idempotencyKey("idemp-1")
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findPendingEvents(any(), any())).thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        scheduler.processOutboxEvents();

        verify(outboxEventRepository, times(2)).saveAndFlush(any(OutboxEvent.class));
        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");

        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("ITEM_EVENT_LOG");
    }

    @Test
    void processOutboxEvents_failure_increments_counter() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .aggregateType("ITEM_EVENT_LOG")
                .aggregateId(100L)
                .eventType("ITEM_PURCHASED")
                .payload("invalid json")
                .idempotencyKey("idemp-1")
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findPendingEvents(any(), any())).thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        scheduler.processOutboxEvents();

        assertThat(event.getStatus()).isEqualTo("PENDING"); // nextAttemptAt should be in the future

        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("FAILURE");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("ITEM_EVENT_LOG");
    }
}
