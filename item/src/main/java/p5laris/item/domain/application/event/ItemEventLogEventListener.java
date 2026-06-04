package p5laris.item.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemEventLogEventListener {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ItemEventLogEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("ITEM_EVENT_LOG")
                    .aggregateId(event.userId() != null ? event.userId() : 0L)
                    .eventType(event.eventType())
                    .payload(payload)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .status("PENDING")
                    .nextAttemptAt(LocalDateTime.now())
                    .build();
                    
            outboxEventRepository.saveAndFlush(outboxEvent);
            log.debug("Saved ItemEventLogEvent to Outbox. eventType={}, userId={}", event.eventType(), event.userId());
        } catch (Exception e) {
            log.error("Failed to save item event log to outbox. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }
}
