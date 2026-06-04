package p5laris.item.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.item.core.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ItemErrorCode implements ErrorCode {
    
    ITEM_NOT_FOUND("ITEM-001", "해당 아이템을 찾을 수 없습니다."),
    ITEM_ALREADY_OWNED("ITEM-002", "이미 보유한 아이템입니다."),
    STAR_PIECE_NOT_ENOUGH("ITEM-003", "별조각이 부족합니다."),
    WALLET_SERVICE_CALL_FAILED("ITEM-004", "지갑 서비스 불러오기를 실패했습니다."),
    USER_ITEM_NOT_FOUND("ITEM-005", "보유한 아이템을 찾을 수 없습니다."),
    ITEM_QUANTITY_NOT_ENOUGH("ITEM-006", "아이템 수량이 부족합니다."),
    INVALID_ITEM_TYPE("ITEM-007", "유효하지 않은 아이템 타입입니다."),
    INVALID_CHARACTER_TYPE("ITEM-008", "유효하지 않은 캐릭터 타입입니다.");

    private final String code;
    private final String message;
}
