package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import p5laris.character.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.character.domain.application.event.StarPieceEarnedEvent;
import p5laris.character.domain.exception.CharacterException;

/**
 * user/wallet 모듈이 발행한 별조각 적립 결과 중 공유 보상 결과만 소비한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareRewardKafkaConsumer {

    private static final String SHARE_REWARD_REASON = "SHARE_REWARD";
    private static final String SHARE_REF_TYPE = "SHARE";

    private final ShareRewardDispatcher shareRewardDispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "star-piece-earned", groupId = "character-group")
    public void handleStarPieceEarned(String messagePayload) {
        StarPieceEarnedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, StarPieceEarnedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 성공 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new IllegalArgumentException("별조각 적립 완료 메시지 역직렬화에 실패했습니다.", e);
        }

        if (!isShareReward(event.getReason(), event.getRefType())) {
            return;
        }

        try {
            shareRewardDispatcher.markSucceeded(event.getOutboxId(), event.getIdempotencyKey());
            log.info("[Kafka] 공유 보상 outbox 성공 확정. shareLogId={}, outboxId={}, transactionId={}",
                    event.getRefId(), event.getOutboxId(), event.getTransactionId());
        } catch (CharacterException e) {
            log.warn("[Kafka] 공유 보상 성공 결과 처리 실패. shareLogId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }

    @KafkaListener(topics = "star-piece-earn-failed", groupId = "character-group")
    public void handleStarPieceEarnFailed(String messagePayload) {
        StarPieceEarnFailedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, StarPieceEarnFailedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 실패 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            throw new IllegalArgumentException("별조각 적립 실패 메시지 역직렬화에 실패했습니다.", e);
        }

        if (!isShareReward(event.getReason(), event.getRefType())) {
            return;
        }

        try {
            shareRewardDispatcher.markFailed(
                    event.getOutboxId(),
                    event.getIdempotencyKey(),
                    event.getErrorCode()
            );
            log.warn("[Kafka] 공유 보상 outbox 재시도 예약. shareLogId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), event.getErrorCode());
        } catch (CharacterException e) {
            log.warn("[Kafka] 공유 보상 실패 결과 처리 실패. shareLogId={}, outboxId={}, errorCode={}",
                    event.getRefId(), event.getOutboxId(), e.getErrorCode().getCode());
        }
    }

    private boolean isShareReward(String reason, String refType) {
        return SHARE_REWARD_REASON.equals(reason) && SHARE_REF_TYPE.equals(refType);
    }
}
