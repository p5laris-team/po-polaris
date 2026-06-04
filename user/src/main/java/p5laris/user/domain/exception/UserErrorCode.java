package p5laris.user.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.user.core.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    
    USER_NOT_FOUND("USER-001", "해당 사용자를 찾을 수 없습니다."),
    INVALID_OAUTH_CODE("USER-002", "유효하지 않은 소셜 로그인 코드입니다."),
    WALLET_NOT_FOUND("USER-003", "해당 지갑을 찾을 수 없습니다."),
    ALREADY_ATTENDED("USER-004", "이미 출석하셨습니다."),
    EARN_AMOUNT_MUST_BE_POSITIVE("USER-005", "보상은 반드시 0 이상입니다."),
    STAR_PIECE_NOT_ENOUGH("USER-006", "별조각이 부족합니다."),
    INVALID_WEATHER_REGION("USER-007", "지원하지 않는 날씨 권역입니다.");

    private final String code;
    private final String message;
}
