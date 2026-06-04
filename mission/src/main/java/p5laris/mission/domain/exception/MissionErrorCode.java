package p5laris.mission.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.mission.core.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCode implements ErrorCode {

    MISSION_NOT_FOUND("MISSION-001", "해당 미션을 찾을 수 없습니다."),
    MISSION_TEMPLATE_NOT_FOUND("MISSION-002", "사용 가능한 미션 템플릿을 찾을 수 없습니다."),
    MISSION_INVALID_STATUS("MISSION-003", "현재 상태에서는 요청한 미션 동작을 처리할 수 없습니다."),
    MISSION_DAILY_LIMIT_EXCEEDED("MISSION-004", "오늘 받을 수 있는 미션 수를 모두 사용했습니다."),
    MISSION_ACTIVE_ALREADY_EXISTS("MISSION-005", "이미 진행 중인 미션이 있습니다."),
    MISSION_ALREADY_COMPLETED("MISSION-006", "이미 완료된 미션입니다."),
    MISSION_ANSWER_INVALID("MISSION-007", "완료 답변은 1자 이상 300자 이하로 입력해야 합니다."),
    MISSION_REWARD_FAILED("MISSION-008", "미션 완료 보상 지급에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    MISSION_REJECT_LIMIT_EXCEEDED("MISSION-009", "오늘 미션 거절 가능 횟수를 초과했습니다."),
    MISSION_FEEDBACK_INVALID("MISSION-010", "미션 피드백 요청이 올바르지 않습니다."),
    MISSION_CHARACTER_EXP_FAILED("MISSION-011", "미션 완료 캐릭터 경험치 지급에 실패했습니다.");

    private final String code;
    private final String message;
}
