package p5laris.user.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import p5laris.user.domain.application.event.ItemPurchaseRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnFailedEvent;
import p5laris.user.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.user.domain.application.event.StarPieceEarnedEvent;
import p5laris.user.domain.application.event.StarPieceSpentEvent;
import p5laris.user.domain.application.event.StarPieceSpendFailedEvent;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

/**
 * User 모듈의 Kafka 메시지 컨슈머 클래스입니다.
 * 
 * [Saga Choreography 흐름에서의 역할]
 * 1. 'item' 모듈로부터 "아이템 구매 요청(item-purchase-requested)" 이벤트를 구독(Consume)합니다.
 * 2. 전달받은 멱등키와 구매 상세 내역을 기반으로 사용자의 재화(별조각)를 차감합니다.
 * 3. 재화 차감의 성공/실패 여부에 따라 각각 후속 이벤트를 발행(Produce)합니다.
 *    - 차감 성공 시: 'star-piece-spent' 이벤트를 발행하여 item 모듈이 구매 처리를 완료할 수 있게 합니다.
 *    - 차감 실패 시: 'star-piece-spend-failed' 이벤트를 발행하여 item 모듈이 구매 트랜잭션을 실패 처리할 수 있게 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserKafkaConsumer {

    private static final String STAR_PIECE_EARN_REQUESTED_TOPIC = "star-piece-earn-requested";
    private static final String STAR_PIECE_EARNED_TOPIC = "star-piece-earned";
    private static final String STAR_PIECE_EARN_FAILED_TOPIC = "star-piece-earn-failed";

    private final WalletService walletService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // 분산 환경의 서로 다른 DTO 패키지 충돌(__TypeId__ 불일치)을 해결하기 위한 수동 역직렬화 매퍼
    private final ObjectMapper objectMapper;

    /**
     * 'item-purchase-requested' 토픽을 구독하여 사용자의 재화를 차감하는 비즈니스 메서드입니다.
     * 패키지 독립성을 확보하기 위해 raw String payload를 수신하여 수동으로 역직렬화합니다.
     *
     * @param messagePayload JSON 포맷의 아이템 구매 요청 정보 문자열
     */
    @KafkaListener(topics = "item-purchase-requested", groupId = "user-group")
    public void handleItemPurchaseRequest(String messagePayload) {
        ItemPurchaseRequestedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, ItemPurchaseRequestedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 구매 요청 메시지 역직렬화(JSON 파싱) 실패 - Payload: {}", messagePayload, e);
            return; // 파싱 실패 시 처리 중단
        }

        log.info("[Kafka] 구매 요청 수신 - 구매 ID: {}, 사용자 ID: {}, 가격: {}",
                event.getPurchaseId(), event.getUserId(), event.getPrice());

        try {
            // 1. WalletService를 통해 실제 별조각 차감 비즈니스 로직 수행
            // WalletService.spendStarPiece 내부에서 중복 요청 처리를 위한 멱등성 검증(Idempotency Key)을 수행함
            StarPieceTransaction tx = walletService.spendStarPiece(
                    event.getUserId(),
                    event.getPrice(),
                    "ITEM_PURCHASE", // 거래 구분 (아이템 구매)
                    "ITEM",          // 대상 엔티티 종류
                    event.getItemId(),
                    event.getIdempotencyKey() // 멱등키 매핑
            );

            // 2. 차감이 성공하면 'star-piece-spent' 이벤트를 빌드하여 발행
            StarPieceSpentEvent successEvent = StarPieceSpentEvent.builder()
                    .purchaseId(event.getPurchaseId())
                    .remainingStarPiece(tx.getBalanceAfter()) // 차감 후 잔액 전달
                    .transactionId(tx.getId()) // 생성된 거래 내역의 트랜잭션 ID 전달
                    .build();

            // Kafka 토픽 'star-piece-spent'로 메시지 전송. 메시지 키로는 멱등키(idempotencyKey)를 사용
            kafkaTemplate.send("star-piece-spent", event.getIdempotencyKey(), successEvent);
            log.info("[Kafka] 재화 차감 성공 이벤트 발행 완료 - 구매 ID: {}", event.getPurchaseId());

        } catch (UserException e) {
            // 3-1. 비즈니스 예외 발생 시 (예: 잔액 부족 등)
            log.warn("[Kafka] 재화 차감 비즈니스 오류 발생: {}", e.getErrorCode());
            
            if (e.getErrorCode() == UserErrorCode.STAR_PIECE_NOT_ENOUGH) {
                // 잔액 부족 실패 이벤트 발행
                StarPieceSpendFailedEvent failEvent = StarPieceSpendFailedEvent.builder()
                        .purchaseId(event.getPurchaseId())
                        .errorCode("STAR_PIECE_NOT_ENOUGH")
                        .build();

                kafkaTemplate.send("star-piece-spend-failed", event.getIdempotencyKey(), failEvent);
                log.info("[Kafka] 잔액 부족 실패 이벤트 발행 완료 - 구매 ID: {}", event.getPurchaseId());
            } else {
                // 기타 정의된 사용자 관련 비즈니스 실패 처리
                String errorCodeStr = (e.getErrorCode() instanceof Enum) ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
                StarPieceSpendFailedEvent failEvent = StarPieceSpendFailedEvent.builder()
                        .purchaseId(event.getPurchaseId())
                        .errorCode(errorCodeStr)
                        .build();
                
                kafkaTemplate.send("star-piece-spend-failed", event.getIdempotencyKey(), failEvent);
                log.info("[Kafka] 기타 비즈니스 실패 이벤트 발행 완료 ({}) - 구매 ID: {}", errorCodeStr, event.getPurchaseId());
            }
        } catch (Exception e) {
            // 3-2. 시스템 인프라 예외 등 예기치 못한 예외 발생 시
            log.error("[Kafka] 재화 차감 처리 중 예기치 못한 오류 발생", e);
            
            // 시스템 예외가 났을 때 컨슈머에서 무한 재시도를 돌려 병목을 일으키기보다,
            // 안전망 차원에서 'SYSTEM_ERROR' 코드를 실어 구매 실패 이벤트를 날리고 보상 트랜잭션(롤백)으로 유도함
            StarPieceSpendFailedEvent failEvent = StarPieceSpendFailedEvent.builder()
                    .purchaseId(event.getPurchaseId())
                    .errorCode("SYSTEM_ERROR")
                    .build();
            
            kafkaTemplate.send("star-piece-spend-failed", event.getIdempotencyKey(), failEvent);
            log.info("[Kafka] 시스템 오류로 인한 실패 이벤트 발행 완료 - 구매 ID: {}", event.getPurchaseId());
        }
    }

    /**
     * 미션/공유/출석 등 다른 모듈이 요청한 별조각 적립을 처리한다.
     *
     * 실제 적립 멱등성은 WalletService가 idempotencyKey로 보장하고,
     * 요청 모듈은 성공/실패 이벤트를 받아 자기 outbox 상태를 확정한다.
     */
    @KafkaListener(topics = STAR_PIECE_EARN_REQUESTED_TOPIC, groupId = "user-group")
    public void handleStarPieceEarnRequested(String messagePayload) {
        StarPieceEarnRequestedEvent event;
        try {
            event = objectMapper.readValue(messagePayload, StarPieceEarnRequestedEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 요청 메시지 역직렬화 실패. payloadLength={}",
                    messagePayload != null ? messagePayload.length() : 0, e);
            return;
        }

        log.info("[Kafka] 별조각 적립 요청 수신. reason={}, refType={}, refId={}, outboxId={}",
                event.getReason(), event.getRefType(), event.getRefId(), event.getOutboxId());

        try {
            StarPieceTransaction tx = walletService.earnStarPiece(
                    event.getUserId(),
                    event.getAmount(),
                    event.getReason(),
                    event.getRefType(),
                    event.getRefId(),
                    event.getIdempotencyKey()
            );

            StarPieceEarnedEvent earnedEvent = StarPieceEarnedEvent.builder()
                    .outboxId(event.getOutboxId())
                    .userId(event.getUserId())
                    .amount(event.getAmount())
                    .reason(event.getReason())
                    .refType(event.getRefType())
                    .refId(event.getRefId())
                    .idempotencyKey(event.getIdempotencyKey())
                    .balanceAfter(tx.getBalanceAfter())
                    .transactionId(tx.getId())
                    .build();

            kafkaTemplate.send(STAR_PIECE_EARNED_TOPIC, event.getIdempotencyKey(), earnedEvent);
            log.info("[Kafka] 별조각 적립 성공 이벤트 발행. reason={}, refId={}, transactionId={}",
                    event.getReason(), event.getRefId(), tx.getId());
        } catch (UserException e) {
            publishEarnFailed(event, e.getErrorCode().getCode());
        } catch (Exception e) {
            log.error("[Kafka] 별조각 적립 처리 중 시스템 오류. reason={}, refId={}",
                    event.getReason(), event.getRefId(), e);
            publishEarnFailed(event, "SYSTEM_ERROR");
        }
    }

    private void publishEarnFailed(StarPieceEarnRequestedEvent event, String errorCode) {
        StarPieceEarnFailedEvent failedEvent = StarPieceEarnFailedEvent.builder()
                .outboxId(event.getOutboxId())
                .userId(event.getUserId())
                .amount(event.getAmount())
                .reason(event.getReason())
                .refType(event.getRefType())
                .refId(event.getRefId())
                .idempotencyKey(event.getIdempotencyKey())
                .errorCode(errorCode)
                .build();

        kafkaTemplate.send(STAR_PIECE_EARN_FAILED_TOPIC, event.getIdempotencyKey(), failedEvent);
        log.warn("[Kafka] 별조각 적립 실패 이벤트 발행. reason={}, refId={}, errorCode={}",
                event.getReason(), event.getRefId(), errorCode);
    }
}
