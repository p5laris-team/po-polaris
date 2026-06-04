package p5laris.user.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventLogEventListener {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(UserEventLogEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("USER_EVENT_LOG")
                    .aggregateId(event.userId() != null ? event.userId() : 0L)
                    .eventType(event.eventType())
                    .payload(payload)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .status("PENDING")
                    .nextAttemptAt(LocalDateTime.now())
                    .build();
                    
            outboxEventRepository.saveAndFlush(outboxEvent);
            log.debug("Saved UserEventLogEvent to Outbox. eventType={}, userId={}", event.eventType(), event.userId());
        } catch (Exception e) {
            log.error("Failed to save user event log to outbox. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }
}
