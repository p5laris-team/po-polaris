package p5laris.character.domain.domain.enums;

/**
 * 공유 이벤트는 항상 기록하되, 별조각 보상은 하루 1회만 지급한다.
 * 프론트는 rewardPaid boolean보다 이 상태값을 우선 신뢰한다.
 */
public enum ShareRewardStatus {
    /** 오늘 아직 공유 보상을 받을 수 있는 상태 */
    AVAILABLE,

    /** 공유 보상 지급 완료 */
    PAID,

    /** 공유는 완료됐고, 별조각 지급은 outbox 재처리로 확인 중 */
    PENDING,

    /** 오늘 공유 보상을 이미 받았거나 지급 대상이 아님 */
    NOT_ELIGIBLE,

    /** 재처리 한도 초과 등으로 지급 불가 */
    FAILED
}
