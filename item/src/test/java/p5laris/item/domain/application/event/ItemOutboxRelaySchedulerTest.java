package p5laris.item.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * ItemOutboxRelayScheduler 클래스의 단위 테스트를 담당하는 테스트 클래스입니다.
 * gRPC 통신이 제거되고 Kafka 기반 비동기 발행으로 전환됨에 따라,
 * KafkaTemplate 모킹을 활용하여 비동기 메시지 발행 성공 및 실패 상황을 검증합니다.
 */
class ItemOutboxRelaySchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    // gRPC 서비스 스텁 대신 주입되는 KafkaTemplate Mock 객체
    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private ItemOutboxRelayScheduler scheduler;

    /**
     * 테스트 실행 전 가상 레지스트리 및 Jackson mapper, Scheduler 인스턴스를 초기화합니다.
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        scheduler = new ItemOutboxRelayScheduler(
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
        when(outboxEventRepository.countByStatus("PENDING")).thenReturn(5L);

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isEqualTo(5.0);
    }

    /**
     * 아웃박스 이벤트 릴레이가 성공했을 때,
     * KafkaTemplate으로 메시지가 발행되고, 아웃박스 엔티티의 상태가 SUCCEEDED로 변경되며,
     * 성공 메트릭 카운터가 1 증가하는지 검증합니다.
     */
    @Test
    void processOutboxEvents_success_increments_counter() {
        // Given: PENDING 상태의 정상 아이템 구매 이벤트 생성
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
        when(kafkaTemplate.send(eq("item-event-logs"), eq("idemp-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When: 아웃박스 릴레이 스케줄러 실행
        scheduler.processOutboxEvents();

        // Then: 'item-event-logs' 토픽으로 카프카 메시지가 정상 전송되었는지 검증
        verify(kafkaTemplate, times(1)).send(eq("item-event-logs"), eq("idemp-1"), any());
        
        // 데이터베이스 갱신(PROCESSING -> SUCCEEDED)이 수행되었는지 검증
        verify(outboxEventRepository, times(2)).saveAndFlush(any(OutboxEvent.class));
        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");

        // 성공 카운터 메트릭 값 검증
        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("ITEM_EVENT_LOG");
    }

    @Test
    void processOutboxEvents_kafka_send_failure_marks_event_retryable() {
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
        when(kafkaTemplate.send(eq("item-event-logs"), eq("idemp-1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        scheduler.processOutboxEvents();

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
    void processOutboxEvents_failure_increments_counter() {
        // Given: 비정상 페이로드(invalid json)를 가진 PENDING 아웃박스 이벤트 생성
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

        // When: 아웃박스 릴레이 스케줄러 실행
        scheduler.processOutboxEvents();

        // Then: 예외가 캐치되고 fail() 메서드를 통해 다음 재시도 시각이 설정되었는지 검증
        assertThat(event.getStatus()).isEqualTo("PENDING"); // 상태는 PENDING 유지 (재시도용)

        // 실패 카운터 메트릭 값 검증
        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("FAILURE");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("ITEM_EVENT_LOG");
    }
}
