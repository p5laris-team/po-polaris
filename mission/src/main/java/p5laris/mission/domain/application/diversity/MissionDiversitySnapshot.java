package p5laris.mission.domain.application.diversity;

import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;

/**
 * AI 후보와 비교할 기존 미션의 최소 스냅샷이다.
 *
 * Entity를 guard까지 끌고 가지 않고, 중복 판단에 필요한 필드만 고정해서 전달한다.
 */
public record MissionDiversitySnapshot(
        Long missionId,
        String title,
        String description,
        MissionCategoryType category,
        UserMissionStatus status,
        MissionActionFamily actionFamily
) {

    public MissionDiversitySnapshot {
        if (actionFamily == null) {
            actionFamily = MissionActionFamilyClassifier.classify(title, description);
        }
    }

    public static MissionDiversitySnapshot from(UserMission mission) {
        return new MissionDiversitySnapshot(
                mission.getId(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getCategory(),
                mission.getStatus(),
                MissionActionFamilyClassifier.classify(mission.getTitle(), mission.getDescription())
        );
    }
}
