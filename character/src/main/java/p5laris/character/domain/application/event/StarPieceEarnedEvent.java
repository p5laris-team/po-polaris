package p5laris.character.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * user/wallet 모듈이 별조각 적립 성공 후 발행하는 Kafka payload다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarPieceEarnedEvent {

    private Long outboxId;
    private Long userId;
    private int amount;
    private String reason;
    private String refType;
    private Long refId;
    private String idempotencyKey;
    private int balanceAfter;
    private Long transactionId;
}
