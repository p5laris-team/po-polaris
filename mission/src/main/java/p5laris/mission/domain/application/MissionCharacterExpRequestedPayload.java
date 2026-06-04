package p5laris.mission.domain.application;

/**
 * 미션 완료로 인한 캐릭터 경험치 지급 요청 outbox payload다.
 */
public record MissionCharacterExpRequestedPayload(
        Long missionId,
        Long userId,
        Long characterId,
        String difficulty,
        Integer expAmount
) {
}
