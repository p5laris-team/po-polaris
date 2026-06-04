package p5laris.gateway.domain.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import p5laris.gateway.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserGatewayErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "해당 사용자를 찾을 수 없습니다."),
    INVALID_WEATHER_REGION(HttpStatus.BAD_REQUEST, "INVALID_WEATHER_REGION", "지원하지 않는 날씨 권역입니다."),
    USER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE", "사용자 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
