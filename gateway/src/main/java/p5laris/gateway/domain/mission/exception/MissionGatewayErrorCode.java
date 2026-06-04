package p5laris.gateway.domain.mission.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import p5laris.gateway.global.exception.ErrorCode;

/**
 * gateway가 mission gRPC 오류를 REST 오류 응답으로 변환할 때 사용하는 에러 코드다.
 */
@Getter
@RequiredArgsConstructor
public enum MissionGatewayErrorCode implements ErrorCode {

    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION_NOT_FOUND", "해당 미션을 찾을 수 없습니다."),
    MISSION_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION_TEMPLATE_NOT_FOUND", "사용 가능한 미션 템플릿을 찾을 수 없습니다."),
    MISSION_INVALID_STATUS(HttpStatus.CONFLICT, "MISSION_INVALID_STATUS", "현재 상태에서는 요청한 미션 동작을 처리할 수 없습니다."),
    MISSION_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "MISSION_DAILY_LIMIT_EXCEEDED", "오늘 받을 수 있는 미션 수를 모두 사용했습니다."),
    MISSION_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "MISSION_ACTIVE_ALREADY_EXISTS", "이미 진행 중인 미션이 있습니다."),
    MISSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "MISSION_ALREADY_COMPLETED", "이미 완료된 미션입니다."),
    MISSION_ANSWER_INVALID(HttpStatus.BAD_REQUEST, "MISSION_ANSWER_INVALID", "완료 답변은 1자 이상 300자 이하로 입력해야 합니다."),
    MISSION_REWARD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "MISSION_REWARD_FAILED", "미션 완료 보상 지급에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    MISSION_REJECT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "MISSION_REJECT_LIMIT_EXCEEDED", "오늘 미션 거절 가능 횟수를 초과했습니다."),
    MISSION_FEEDBACK_INVALID(HttpStatus.BAD_REQUEST, "MISSION_FEEDBACK_INVALID", "미션 피드백 요청이 올바르지 않습니다."),
    MISSION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MISSION_SERVICE_UNAVAILABLE", "미션 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
