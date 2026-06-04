package p5laris.mission.domain.application;

/**
 * 미션 완료 보상 지급 요청 outbox payload다.
 *
 * mission_outbox_events는 공용 이벤트 테이블이라 보상에 필요한 값은 JSONB payload에 담는다.
 */
public record MissionRewardRequestedPayload(
        Long missionId,
        Long userId,
        Integer rewardStarPiece
) {
}
