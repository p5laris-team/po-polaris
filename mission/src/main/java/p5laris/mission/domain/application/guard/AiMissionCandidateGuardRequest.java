package p5laris.mission.domain.application.guard;

import p5laris.mission.domain.application.diversity.MissionDiversitySnapshot;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 미션 후보를 mission 서버 정책으로 최종 검증할 때 필요한 입력값이다.
 *
 * guard는 DB를 직접 조회하지 않고, 서비스 계층이 이미 계산한 정책 상태만 넘겨받는다.
 */
public record AiMissionCandidateGuardRequest(
        Long userId,
        Long missionId,
        MissionCategoryType fallbackCategory,
        LocalDateTime offeredAt,
        boolean challengeAlreadyUsedToday,
        List<MissionDiversitySnapshot> todayMissions,
        AiMissionTextResult candidate
) {

    public AiMissionCandidateGuardRequest {
        todayMissions = todayMissions == null ? List.of() : List.copyOf(todayMissions);
    }

    public AiMissionCandidateGuardRequest(
            Long userId,
            Long missionId,
            MissionCategoryType fallbackCategory,
            LocalDateTime offeredAt,
            boolean challengeAlreadyUsedToday,
            AiMissionTextResult candidate
    ) {
        this(userId, missionId, fallbackCategory, offeredAt, challengeAlreadyUsedToday, List.of(), candidate);
    }
}
