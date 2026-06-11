package p5laris.mission.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.event.MissionCharacterExpFailedEvent;
import p5laris.mission.domain.application.event.MissionCharacterExpGrantedEvent;
import p5laris.mission.domain.exception.KafkaConsumerProcessingException;
import p5laris.mission.domain.exception.MissionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCharacterExpKafkaConsumer {

    private final MissionCharacterExpDispatcher missionCharacterExpDispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "mission-character-exp-granted", groupId = "mission-group")
    public void handleMissionCharacterExpGranted(String messagePayload) {
        MissionCharacterExpGrantedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, MissionCharacterExpGrantedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 미션 캐릭터 경험치 성공 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new KafkaConsumerProcessingException("미션 캐릭터 경험치 지급 완료 메시지 역직렬화에 실패했습니다.", e);
        }

        try {
            missionCharacterExpDispatcher.markSucceeded(event.getOutboxId(), event.getIdempotencyKey());
            log.info("[Kafka] 미션 캐릭터 경험치 outbox 성공 확정. missionId={}, outboxId={}",
                    event.getMissionId(), event.getOutboxId());
        } catch (MissionException e) {
            log.warn("[Kafka] 미션 캐릭터 경험치 성공 결과 처리 실패. missionId={}, outboxId={}, errorCode={}",
                    event.getMissionId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }

    @KafkaListener(topics = "mission-character-exp-failed", groupId = "mission-group")
    public void handleMissionCharacterExpFailed(String messagePayload) {
        MissionCharacterExpFailedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, MissionCharacterExpFailedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 미션 캐릭터 경험치 실패 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new KafkaConsumerProcessingException("미션 캐릭터 경험치 지급 실패 메시지 역직렬화에 실패했습니다.", e);
        }

        try {
            missionCharacterExpDispatcher.markFailed(
                    event.getOutboxId(),
                    event.getIdempotencyKey(),
                    event.getErrorCode()
            );
            log.warn("[Kafka] 미션 캐릭터 경험치 outbox 재시도 예약. missionId={}, outboxId={}, errorCode={}",
                    event.getMissionId(), event.getOutboxId(), event.getErrorCode());
        } catch (MissionException e) {
            log.warn("[Kafka] 미션 캐릭터 경험치 실패 결과 처리 실패. missionId={}, outboxId={}, errorCode={}",
                    event.getMissionId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }
}
