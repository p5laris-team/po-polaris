package p5laris.ai.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;

import java.util.Optional;

/**
 * ai_mission_generations 저장 repository다.
 */
public interface AiMissionGenerationRepository extends JpaRepository<AiMissionGeneration, Long> {

    // request_id는 AI 생성 요청을 추적하고 같은 요청을 재사용하기 위한 멱등 기준이다.
    Optional<AiMissionGeneration> findByRequestId(String requestId);
}
