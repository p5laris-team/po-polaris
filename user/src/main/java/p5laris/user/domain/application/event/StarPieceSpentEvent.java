package p5laris.user.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재화(별조각) 차감 성공 이벤트 DTO 입니다.
 * User 모듈에서 재화 차감 성공 시 발행하며, Item 모듈이 이를 수신하여 인벤토리 지급 처리를 수행합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarPieceSpentEvent {

    /** 구매 주문 건의 ID (UserItemPurchase 엔티티 ID) */
    private Long purchaseId;

    /** 차감 후 남은 사용자의 별조각 잔액 */
    private int remainingStarPiece;

    /** User 모듈 지갑(Wallet)에서 생성된 거래(Transaction) 내역의 고유 ID */
    private Long transactionId;
}
