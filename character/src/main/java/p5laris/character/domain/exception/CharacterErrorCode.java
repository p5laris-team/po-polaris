package p5laris.character.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CharacterErrorCode {
    CHARACTER_NOT_FOUND("CH001", "캐릭터를 찾을 수 없습니다."),
    CHARACTER_TYPE_NOT_FOUND("CH002", "캐릭터 타입을 찾을 수 없습니다."),
    NOT_CHARACTER_OWNER("CH003", "해당 캐릭터의 소유자가 아닙니다."),
    INVALID_CHARACTER_NAME("CH004", "유효하지 않은 캐릭터 이름입니다."),
    INVALID_ACTION_TYPE("CH005", "유효하지 않은 돌봄 액션입니다."),
    SHARE_CARD_NOT_FOUND("CH006", "공유 카드를 찾을 수 없습니다."),
    NOT_SHARE_CARD_OWNER("CH007", "해당 공유 카드의 소유자가 아닙니다."),
    SHARE_LINK_NOT_FOUND("CH008", "공유 링크를 찾을 수 없습니다."),
    INVALID_SHARE_HEADLINE("CH009", "공유 카드 문구가 유효하지 않습니다."),
    ITEM_NOT_OWNED("CH011", "보유하지 않은 아이템입니다."),
    INVALID_CARE_ITEM("CH012", "돌봄 액션에 사용할 수 없는 아이템입니다."),
    ITEM_QUANTITY_NOT_ENOUGH("CH013", "아이템 수량이 부족합니다."),
    INVALID_SHARE_CARD_IMAGE_URL("CH014", "공유 카드 이미지 URL이 유효하지 않습니다."),
    ITEM_SERVICE_CALL_FAILED("CH015", "아이템 서비스 호출에 실패했습니다."),
    SHARE_REWARD_FAILED("CH016", "공유 보상 지급에 실패했습니다."),
    INVALID_IDEMPOTENCY_KEY("CH017", "멱등키가 유효하지 않습니다."),
    INVALID_EXP_GRANT_REQUEST("CH018", "캐릭터 경험치 지급 요청이 유효하지 않습니다."),
    INVALID_INTERACTION_TYPE("CH019", "캐릭터 상호작용 타입이 유효하지 않습니다.");

    private final String code;
    private final String message;
}
