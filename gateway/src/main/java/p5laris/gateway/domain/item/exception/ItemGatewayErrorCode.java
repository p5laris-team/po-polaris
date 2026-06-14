package p5laris.gateway.domain.item.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import p5laris.gateway.global.exception.ErrorCode;

/**
 * gateway가 item gRPC 오류를 REST 오류 응답으로 변환할 때 사용하는 에러 코드다.
 */
@Getter
@RequiredArgsConstructor
public enum ItemGatewayErrorCode implements ErrorCode {

    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "해당 아이템을 찾을 수 없습니다."),
    ITEM_ALREADY_OWNED(HttpStatus.CONFLICT, "ITEM_ALREADY_OWNED", "이미 보유한 아이템입니다."),
    STAR_PIECE_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "STAR_PIECE_NOT_ENOUGH", "별조각이 부족합니다."),
    ITEM_QUANTITY_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "ITEM_QUANTITY_NOT_ENOUGH", "아이템 수량이 부족합니다."),
    USER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_ITEM_NOT_FOUND", "보유한 아이템을 찾을 수 없습니다."),
    ITEM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ITEM_SERVICE_UNAVAILABLE", "아이템 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
