package p5laris.user.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다른 모듈이 user/wallet 모듈에 별조각 적립을 요청할 때 사용하는 Kafka payload다.
 *
 * user 모듈은 reason/refType/refId를 거래 내역에 그대로 저장하고,
 * 요청을 보낸 모듈은 outboxId와 idempotencyKey로 자기 outbox를 확정한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarPieceEarnRequestedEvent {

    private Long outboxId;
    private Long userId;
    private int amount;
    private String reason;
    private String refType;
    private Long refId;
    private String idempotencyKey;
}
