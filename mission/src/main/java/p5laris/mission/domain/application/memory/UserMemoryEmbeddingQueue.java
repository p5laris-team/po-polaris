package p5laris.mission.domain.application.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.infrastructure.config.MissionRagProperties;
import p5laris.mission.domain.infrastructure.repository.UserMemoryEmbeddingJdbcRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * user_memories 저장 트랜잭션 안에서 embedding 생성 대기 row만 만든다.
 *
 * 외부 AI 호출은 하지 않으므로 미션 완료/피드백 저장 흐름을 느리게 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class UserMemoryEmbeddingQueue {

    private final UserMemoryEmbeddingJdbcRepository userMemoryEmbeddingJdbcRepository;
    private final MissionRagProperties missionRagProperties;
    private final Clock clock;

    public void enqueue(UserMemory memory) {
        if (memory == null || memory.getId() == null || !missionRagProperties.isEnabled()) {
            return;
        }

        userMemoryEmbeddingJdbcRepository.upsertPending(
                memory,
                missionRagProperties.getEmbeddingModel(),
                missionRagProperties.getEmbeddingDimension(),
                LocalDateTime.now(clock)
        );
    }
}
