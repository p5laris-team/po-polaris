package p5laris.mission.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.mission.domain.application.event.StarPieceEarnedEvent;
import p5laris.mission.domain.exception.MissionException;

/**
 * user/wallet 모듈이 발행한 별조각 적립 결과 중 미션 보상 결과만 소비한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionRewardKafkaConsumer {

    private static final String MISSION_REWARD_REASON = "MISSION_REWARD";
    private static final String MISSION_REF_TYPE = "MISSION";

    private final MissionRewardDispatcher missionRewardDispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "star-piece-earned", groupId = "mission-group")
    public void handleStarPieceEarned(String messagePayload) {
        StarPieceEarnedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, StarPieceEarnedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 성공 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new IllegalArgumentException("별조각 적립 완료 메시지 역직렬화에 실패했습니다.", e);
        }

        if (!isMissionReward(event.getReason(), event.getRefType())) {
            return;
        }

        try {
            missionRewardDispatcher.markSucceeded(event.getOutboxId(), event.getIdempotencyKey());
            log.info("[Kafka] 미션 보상 outbox 성공 확정. missionId={}, outboxId={}, transactionId={}",
                    event.getRefId(), event.getOutboxId(), event.getTransactionId());
        } catch (MissionException e) {
            log.warn("[Kafka] 미션 보상 성공 결과 처리 실패. missionId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }

    @KafkaListener(topics = "star-piece-earn-failed", groupId = "mission-group")
    public void handleStarPieceEarnFailed(String messagePayload) {
        StarPieceEarnFailedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, StarPieceEarnFailedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 실패 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new IllegalArgumentException("별조각 적립 실패 메시지 역직렬화에 실패했습니다.", e);
        }

        if (!isMissionReward(event.getReason(), event.getRefType())) {
            return;
        }

        try {
            missionRewardDispatcher.markFailed(
                    event.getOutboxId(),
                    event.getIdempotencyKey(),
                    event.getErrorCode()
            );
            log.warn("[Kafka] 미션 보상 outbox 재시도 예약. missionId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), event.getErrorCode());
        } catch (MissionException e) {
            log.warn("[Kafka] 미션 보상 실패 결과 처리 실패. missionId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }

    private boolean isMissionReward(String reason, String refType) {
        return MISSION_REWARD_REASON.equals(reason) && MISSION_REF_TYPE.equals(refType);
    }
}
