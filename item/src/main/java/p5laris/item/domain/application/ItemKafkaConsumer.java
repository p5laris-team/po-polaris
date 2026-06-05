package p5laris.item.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.item.domain.application.event.ItemEventLogEvent;
import p5laris.item.domain.application.event.StarPieceSpentEvent;
import p5laris.item.domain.application.event.StarPieceSpendFailedEvent;
import p5laris.item.domain.domain.entity.UserItem;
import p5laris.item.domain.domain.entity.UserItemPurchase;
import p5laris.item.domain.domain.repository.UserItemPurchaseRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * Item 모듈의 Kafka 메시지 컨슈머 클래스입니다.
 * 
 * [Saga Choreography 흐름에서의 역할]
 * 1. 'user' 모듈에서 수행한 재화 차감 결과를 비동기적으로 구독(Consume)합니다.
 * 2. 'star-piece-spent' (차감 성공) 이벤트를 수신한 경우:
 *    - PENDING 상태의 구매 건을 COMPLETED(완료) 상태로 변경하고, 아이템 수량을 인벤토리에 지급합니다.
 *    - 통계 분석 및 로깅을 위해 애플리케이션 내부 이벤트를 발행합니다.
 * 3. 'star-piece-spend-failed' (차감 실패) 이벤트를 수신한 경우:
 *    - PENDING 상태의 구매 건을 FAILED(실패) 상태로 마킹하여 트랜잭션을 중단(롤백)합니다.
 */
public class ItemKafkaConsumer {

    private final UserItemPurchaseRepository userItemPurchaseRepository;
    private final UserItemRepository userItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * 재화 차감이 성공적으로 완료되었음을 알리는 'star-piece-spent' 이벤트를 구독하여 처리합니다.
     *
     * @param event 재화 차감 성공 상세 데이터가 담긴 이벤트 DTO
     */
    @KafkaListener(topics = "star-piece-spent", groupId = "item-group")
    public void handleStarPieceSpent(StarPieceSpentEvent event) {
        log.info("[Kafka] 재화 차감 완료 수신 - 구매 ID: {}, 잔여 재화: {}, 트랜잭션 ID: {}",
                event.getPurchaseId(), event.getRemainingStarPiece(), event.getTransactionId());

        // 1. TransactionTemplate을 사용하여 독립적인 트랜잭션 내에서 영속성 상태 보장
        transactionTemplate.execute(status -> {
            UserItemPurchase p = userItemPurchaseRepository.findById(event.getPurchaseId()).orElse(null);
            if (p == null) {
                log.warn("[Kafka] 구매 정보를 찾을 수 없습니다. 구매 ID: {}", event.getPurchaseId());
                return null;
            }

            // 2. 구매 건의 현재 상태가 PENDING인 경우에만 성공 상태로 확정 처리 (멱등적 처리)
            if ("PENDING".equals(p.getStatus())) {
                p.updateStatus("COMPLETED"); // 상태 완료 전환
                p.updateSuccessData(event.getRemainingStarPiece(), event.getTransactionId()); // 외부 트랜잭션 매핑
                userItemPurchaseRepository.save(p);

                // 3. 인벤토리(UserItem)에 구매 수량만큼 아이템 추가 지급
                UserItem uItem = p.getUserItem();
                uItem.addQuantity(p.getQuantity());
                userItemRepository.save(uItem);

                // 4. 구매 기록 및 재화 차감 통계를 기록하기 위해 Spring Event Publisher를 통해 이벤트를 던집니다.
                // 이 이벤트들은 'event-log' 모듈 등으로 나중에 적재될 수 있습니다.
                eventPublisher.publishEvent(ItemEventLogEvent.itemPurchased(
                        p.getUserId(), uItem, uItem.getItem(), p.getQuantity(), p.getPrice(),
                        event.getTransactionId(), event.getRemainingStarPiece()
                ));
                eventPublisher.publishEvent(ItemEventLogEvent.starPieceSpent(
                        p.getUserId(), uItem.getItem(), p.getPrice(), event.getTransactionId(),
                        event.getRemainingStarPiece(), p.getIdempotencyKey() != null ? p.getIdempotencyKey() : ""
                ));
                log.info("[Kafka] 구매 프로세스 최종 완료 및 아이템 지급 완료 - 구매 ID: {}", p.getId());
            } else {
                // 이미 타 프로세스(예: 보상 스케줄러 등)에 의해 완료/실패 처리된 경우 중복 처리하지 않음
                log.info("[Kafka] 이미 처리 완료된 구매 건입니다. 상태: {}, 구매 ID: {}", p.getStatus(), p.getId());
            }
            return null;
        });
    }

    /**
     * 재화 차감이 실패했음을 알리는 'star-piece-spend-failed' 이벤트를 구독하여 처리합니다.
     *
     * @param event 재화 차감 실패 정보가 담긴 이벤트 DTO
     */
    @KafkaListener(topics = "star-piece-spend-failed", groupId = "item-group")
    public void handleStarPieceSpendFailed(StarPieceSpendFailedEvent event) {
        log.info("[Kafka] 재화 차감 실패 수신 - 구매 ID: {}, 실패 코드: {}",
                event.getPurchaseId(), event.getErrorCode());

        // 1. TransactionTemplate을 사용하여 독립적인 트랜잭션 내에서 영속성 상태 보장
        transactionTemplate.execute(status -> {
            UserItemPurchase p = userItemPurchaseRepository.findById(event.getPurchaseId()).orElse(null);
            if (p == null) {
                log.warn("[Kafka] 구매 정보를 찾을 수 없습니다. 구매 ID: {}", event.getPurchaseId());
                return null;
            }

            // 2. 구매 건의 현재 상태가 PENDING인 경우에만 실패 상태로 전환
            if ("PENDING".equals(p.getStatus())) {
                p.updateStatus("FAILED"); // 상태 실패 전환
                userItemPurchaseRepository.save(p);
                log.info("[Kafka] 구매 실패(FAILED) 마킹 완료 - 구매 ID: {}", p.getId());
            } else {
                log.info("[Kafka] 이미 처리 완료된 구매 건입니다. 상태: {}, 구매 ID: {}", p.getStatus(), p.getId());
            }
            return null;
        });
    }
}
