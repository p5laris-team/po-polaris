package p5laris.mission.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.MissionFeedback;
import p5laris.mission.domain.domain.enums.MissionFeedbackType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionFeedbackRepository extends JpaRepository<MissionFeedback, Long> {

    Optional<MissionFeedback> findByUserIdAndMissionIdAndFeedbackType(
            Long userId,
            Long missionId,
            MissionFeedbackType feedbackType
    );

    List<MissionFeedback> findByUserIdAndMissionIdIn(Long userId, Collection<Long> missionIds);
}
