package p5laris.gateway.domain.share.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import p5laris.gateway.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ShareGatewayErrorCode implements ErrorCode {
    INVALID_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "INVALID_IDEMPOTENCY_KEY", "멱등키가 유효하지 않습니다."),
    INVALID_SHARE_CARD_IMAGE_URL(HttpStatus.BAD_REQUEST, "INVALID_SHARE_CARD_IMAGE_URL", "공유 카드 이미지 URL이 유효하지 않습니다."),
    SHARE_CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "SHARE_CARD_NOT_FOUND", "공유 카드를 찾을 수 없습니다."),
    NOT_SHARE_CARD_OWNER(HttpStatus.FORBIDDEN, "NOT_SHARE_CARD_OWNER", "해당 공유 카드의 소유자가 아닙니다."),
    SHARE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "SHARE_LINK_NOT_FOUND", "공유 링크를 찾을 수 없습니다."),
    INVALID_SHARE_HEADLINE(HttpStatus.BAD_REQUEST, "INVALID_SHARE_HEADLINE", "공유 카드 문구가 유효하지 않습니다."),
    SHARE_REWARD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_REWARD_FAILED", "공유 보상 지급에 실패했습니다."),
    SHARE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_SERVICE_UNAVAILABLE", "공유 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
