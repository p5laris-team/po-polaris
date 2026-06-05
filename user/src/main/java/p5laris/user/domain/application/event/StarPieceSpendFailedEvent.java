package p5laris.user.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재화(별조각) 차감 실패 이벤트 DTO 입니다.
 * User 모듈에서 재화 차감 실패 시 발행하며, Item 모듈이 이를 수신하여 구매 요청을 실패 처리(롤백)합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarPieceSpendFailedEvent {

    /** 구매 주문 건의 ID (UserItemPurchase 엔티티 ID) */
    private Long purchaseId;

    /** 차감 실패 사유 코드 (예: STAR_PIECE_NOT_ENOUGH, SYSTEM_ERROR 등) */
    private String errorCode;
}
