package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import p5laris.character.domain.application.dto.GrantCharacterExpResponse;
import p5laris.character.domain.application.event.MissionCharacterExpFailedEvent;
import p5laris.character.domain.application.event.MissionCharacterExpGrantedEvent;
import p5laris.character.domain.application.event.MissionCharacterExpRequestedEvent;
import p5laris.character.domain.exception.CharacterException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterKafkaConsumer {

    private static final String EXP_GRANTED_TOPIC = "mission-character-exp-granted";
    private static final String EXP_FAILED_TOPIC = "mission-character-exp-failed";
    private static final String SOURCE_TYPE_MISSION_COMPLETION = "MISSION_COMPLETION";

    private final CharacterService characterService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "mission-character-exp-requested", groupId = "character-group")
    public void handleMissionCharacterExpRequested(String messagePayload) {
        MissionCharacterExpRequestedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, MissionCharacterExpRequestedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 미션 캐릭터 경험치 요청 메시지 역직렬화 실패. payload={}", messagePayload, e);
            return;
        }

        log.info("[Kafka] 미션 캐릭터 경험치 요청 수신. missionId={}, characterId={}, outboxId={}",
                event.getMissionId(), event.getCharacterId(), event.getOutboxId());

        try {
            GrantCharacterExpResponse response = characterService.grantCharacterExp(
                    event.getUserId(),
                    event.getCharacterId(),
                    SOURCE_TYPE_MISSION_COMPLETION,
                    event.getMissionId(),
                    event.getExpAmount(),
                    event.getIdempotencyKey()
            );

            MissionCharacterExpGrantedEvent grantedEvent = MissionCharacterExpGrantedEvent.builder()
                    .outboxId(event.getOutboxId())
                    .idempotencyKey(event.getIdempotencyKey())
                    .missionId(event.getMissionId())
                    .characterId(response.characterId())
                    .expGained(response.expGained())
                    .levelUp(response.levelUp())
                    .alreadyProcessed(response.alreadyProcessed())
                    .build();
            kafkaTemplate.send(EXP_GRANTED_TOPIC, event.getIdempotencyKey(), grantedEvent);
            log.info("[Kafka] 미션 캐릭터 경험치 지급 성공 이벤트 발행. missionId={}, characterId={}",
                    event.getMissionId(), event.getCharacterId());
        } catch (CharacterException e) {
            publishFailure(event, e.getErrorCode().getCode());
        } catch (Exception e) {
            log.error("[Kafka] 미션 캐릭터 경험치 지급 중 시스템 오류. missionId={}, characterId={}",
                    event.getMissionId(), event.getCharacterId(), e);
            publishFailure(event, "SYSTEM_ERROR");
        }
    }

    private void publishFailure(MissionCharacterExpRequestedEvent event, String errorCode) {
        MissionCharacterExpFailedEvent failedEvent = MissionCharacterExpFailedEvent.builder()
                .outboxId(event.getOutboxId())
                .idempotencyKey(event.getIdempotencyKey())
                .missionId(event.getMissionId())
                .errorCode(errorCode)
                .build();
        kafkaTemplate.send(EXP_FAILED_TOPIC, event.getIdempotencyKey(), failedEvent);
        log.warn("[Kafka] 미션 캐릭터 경험치 지급 실패 이벤트 발행. missionId={}, characterId={}, errorCode={}",
                event.getMissionId(), event.getCharacterId(), errorCode);
    }
}
