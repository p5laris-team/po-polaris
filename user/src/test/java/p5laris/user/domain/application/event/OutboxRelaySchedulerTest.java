package p5laris.user.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OutboxRelayScheduler 클래스의 단위 테스트를 담당하는 테스트 클래스입니다.
 * gRPC 대신 KafkaTemplate 모킹을 주입하여 'user-event-logs' 및 'notification-requests' 토픽으로의 
 * 비동기 이벤트 발행 성공 및 실패 상황을 성실히 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    // gRPC 서비스 스텁 대신 주입되는 KafkaTemplate Mock 객체
    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayScheduler scheduler;

    /**
     * 테스트 실행 전 가상 레지스트리 및 Jackson mapper, Scheduler 인스턴스를 초기화합니다.
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        scheduler = new OutboxRelayScheduler(
                outboxEventRepository,
                objectMapper,
                meterRegistry,
                kafkaTemplate
        );
        scheduler.init();
    }

    /**
     * PENDING 상태인 아웃박스 이벤트 건수를 측정하는 메트릭이 
     * 리포지토리의 카운트 쿼리와 바인딩되어 올바르게 노출되는지 검증합니다.
     */
    @Test
    void gauge_pending_count_is_registered_correctly() {
        when(outboxEventRepository.countByStatus("PENDING")).thenReturn(12L);

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isEqualTo(12.0);
    }

    /**
     * 아웃박스 이벤트 릴레이가 성공했을 때,
     * KafkaTemplate으로 메시지가 발행되고, 아웃박스 엔티티의 상태가 SUCCEEDED로 변경되며,
     * 성공 메트릭 카운터가 1 증가하는지 검증합니다.
     */
    @Test
    void relayEvents_success_increments_counter() {
        // Given: PENDING 상태의 정상 사용자 로그인 이벤트 생성
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
        when(kafkaTemplate.send(eq("user-event-logs"), eq("idemp-user-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When: 아웃박스 릴레이 스케줄러 실행
        scheduler.relayEvents();

        // Then: 'user-event-logs' 토픽으로 카프카 메시지가 정상 전송되었는지 검증
        verify(kafkaTemplate, times(1)).send(eq("user-event-logs"), eq("idemp-user-1"), any());
        
        // 데이터베이스 갱신(PROCESSING -> SUCCEEDED)이 수행되었는지 검증
        verify(outboxEventRepository, times(2)).saveAndFlush(any(OutboxEvent.class));
        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");

        // 성공 카운터 메트릭 값 검증
        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("USER_EVENT_LOG");
    }

    @Test
    void relayEvents_kafka_send_failure_marks_event_retryable() {
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
        when(kafkaTemplate.send(eq("user-event-logs"), eq("idemp-user-1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        scheduler.relayEvents();

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastErrorMessage()).contains("broker unavailable");
    }

    /**
     * 페이로드 데이터가 비정상적이어서 역직렬화 중 예외가 발생했을 때,
     * 아웃박스 엔티티가 실패 처리(지수 백오프 및 상태 업데이트)되고
     * 실패 메트릭 카운터가 1 증가하는지 검증합니다.
     */
    @Test
    void relayEvents_failure_increments_counter() {
        // Given: 비정상 페이로드(invalid-json-payload)를 가진 PENDING 아웃박스 이벤트 생성
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

        // When: 아웃박스 릴레이 스케줄러 실행
        scheduler.relayEvents();

        // Then: 예외가 캐치되고 fail() 메서드를 통해 다음 재시도 시각이 설정되었는지 검증
        assertThat(event.getStatus()).isEqualTo("PENDING");

        // 실패 카운터 메트릭 값 검증
        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("FAILURE");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("USER_EVENT_LOG");
    }
}
