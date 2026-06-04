package p5laris.mission.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionCompletionAnswerRepository extends JpaRepository<MissionCompletionAnswer, Long> {

    // 미션당 완료 질문/답변은 하나만 존재한다.
    Optional<MissionCompletionAnswer> findByMissionId(Long missionId);

    // 히스토리 목록에서 질문/답변 미리보기를 함께 내려주기 위해 한 번에 조회한다.
    List<MissionCompletionAnswer> findByMissionIdIn(Collection<Long> missionIds);
}
