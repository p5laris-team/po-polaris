package p5laris.character.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterEventLogEventListener {

    public static final String AGGREGATE_TYPE_CHARACTER_EVENT_LOG = "CHARACTER_EVENT_LOG";

    private final ObjectMapper objectMapper;
    private final CharacterOutboxEventRepository characterOutboxEventRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(CharacterEventLogEvent event) {
        try {
            CharacterOutboxEvent outboxEvent = CharacterOutboxEvent.pending(
                    AGGREGATE_TYPE_CHARACTER_EVENT_LOG,
                    event.refId(),
                    event.eventType(),
                    objectMapper.valueToTree(event),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now()
            );
            characterOutboxEventRepository.saveAndFlush(outboxEvent);
        } catch (Exception e) {
            log.error("캐릭터 이벤트 로그 outbox 저장에 실패했습니다. eventType={}", event.eventType(), e);
        }
    }
}
