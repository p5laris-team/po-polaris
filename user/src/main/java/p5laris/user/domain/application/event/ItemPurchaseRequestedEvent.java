package p5laris.user.domain.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템 구매 요청 이벤트 DTO 입니다.
 * Item 모듈에서 구매 생성 시 발행하여 User 모듈의 재화 차감 프로세스를 시작하게 합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPurchaseRequestedEvent {

    /** 구매 주문 건의 ID (UserItemPurchase 엔티티 ID) */
    private Long purchaseId;

    /** 구매를 요청한 사용자의 ID */
    private Long userId;

    /** 구매하고자 하는 아이템의 ID */
    private Long itemId;

    /** 차감해야 할 별조각의 금액 */
    private int price;

    /** 중복 처리를 방지하기 위한 멱등성 검증용 고유 키 */
    private String idempotencyKey;
}
