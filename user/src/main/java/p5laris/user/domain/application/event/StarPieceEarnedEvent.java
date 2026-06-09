package p5laris.user.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 별조각 적립 성공 결과를 요청 모듈로 되돌려주는 Kafka payload다.
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
