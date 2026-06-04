package p5laris.ai.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.ai.domain.domain.entity.AiUsageLog;

import java.util.Optional;

/**
 * ai_usage_logs 저장/조회 repository다.
 */
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    // request_id 중복 처리 방지를 위해 이미 처리한 요청인지 확인한다.
    Optional<AiUsageLog> findByRequestId(String requestId);
}
