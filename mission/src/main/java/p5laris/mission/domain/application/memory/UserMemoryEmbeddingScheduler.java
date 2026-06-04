package p5laris.mission.domain.application.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionMemoryEmbeddingProperties;

/**
 * 사용자 기억 embedding 생성 대기열을 주기적으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryEmbeddingScheduler {

    private final UserMemoryEmbeddingDispatcher userMemoryEmbeddingDispatcher;
    private final MissionMemoryEmbeddingProperties properties;

    @Scheduled(
            fixedDelayString = "${mission.memory-embedding.fixed-delay-ms}",
            initialDelayString = "${mission.memory-embedding.initial-delay-ms}"
    )
    public void dispatchUserMemoryEmbeddings() {
        if (!properties.isEnabled()) {
            return;
        }

        int succeededCount = userMemoryEmbeddingDispatcher.dispatchDue(properties.getBatchSize());
        if (succeededCount > 0) {
            log.info("사용자 기억 embedding 생성 완료. 성공건수={}", succeededCount);
        }
    }
}
