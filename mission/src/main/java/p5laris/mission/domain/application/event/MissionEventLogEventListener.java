package p5laris.mission.domain.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * mission 비즈니스 이벤트를 트랜잭션 커밋 후 event-log 모듈로 전송한다.
 *
 * 로깅 실패가 미션 생성/완료 같은 핵심 흐름으로 전파되지 않도록 예외는 warn 로그로만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionEventLogEventListener {

    private static final String MISSION_EVENT_LOG_TOPIC = "mission-event-logs";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MissionEventLogEvent event) {
        String eventId = UUID.randomUUID().toString();
        try {
            kafkaTemplate.send(MISSION_EVENT_LOG_TOPIC, eventId, event);
        } catch (Exception e) {
            log.warn("미션 이벤트 로그 Kafka 발행 실패. eventType={}, userId={}, refId={}",
                    event.eventType(), event.userId(), event.refId(), e);
        }
    }
}
