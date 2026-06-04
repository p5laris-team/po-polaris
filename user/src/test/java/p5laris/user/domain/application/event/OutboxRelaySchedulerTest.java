package p5laris.user.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @Mock
    private com.p5laris.proto.notification.v1.NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        scheduler = new OutboxRelayScheduler(
                outboxEventRepository,
                objectMapper,
                meterRegistry
        );
        ReflectionTestUtils.setField(scheduler, "eventLogStub", eventLogStub);
        ReflectionTestUtils.setField(scheduler, "notificationStub", notificationStub);
        ReflectionTestUtils.setField(scheduler, "sourceService", "user");
        scheduler.init();
    }

    @Test
    void gauge_pending_count_is_registered_correctly() {
        when(outboxEventRepository.countByStatus("PENDING")).thenReturn(12L);

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isEqualTo(12.0);
    }

    @Test
    void relayEvents_success_increments_counter() {
        OutboxEvent event = OutboxEvent.builder()
                .id(10L)
                .aggregateType("USER_EVENT_LOG")
                .aggregateId(200L)
                .eventType("USER_LOGGED_IN")
                .payload("{\"eventType\":\"USER_LOGGED_IN\",\"userId\":1,\"refType\":\"USER\",\"refId\":1,\"metadata\":{},\"occurredAt\":\"2026-06-02T14:15:00+09:00\"}")
                .idempotencyKey("idemp-user-1")
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findPendingEvents(any(), any())).thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));

        scheduler.relayEvents();

        verify(outboxEventRepository, times(2)).saveAndFlush(any(OutboxEvent.class));
        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");

        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("USER_EVENT_LOG");
    }

    @Test
    void relayEvents_failure_increments_counter() {
        OutboxEvent event = OutboxEvent.builder()
                .id(10L)
                .aggregateType("USER_EVENT_LOG")
                .aggregateId(200L)
                .eventType("USER_LOGGED_IN")
                .payload("invalid-json-payload")
                .idempotencyKey("idemp-user-1")
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findPendingEvents(any(), any())).thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));

        scheduler.relayEvents();

        assertThat(event.getStatus()).isEqualTo("PENDING");

        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("FAILURE");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("USER_EVENT_LOG");
    }
}
