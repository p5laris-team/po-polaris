package p5laris.character.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * character/share 모듈이 user/wallet 모듈에 별조각 적립을 요청할 때 사용하는 Kafka payload다.
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
