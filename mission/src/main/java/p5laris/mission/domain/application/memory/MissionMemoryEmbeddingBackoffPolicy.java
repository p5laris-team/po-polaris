package p5laris.mission.domain.application.memory;

import org.springframework.stereotype.Component;
import p5laris.common.outbox.OutboxBackoffPolicy;
import p5laris.mission.domain.infrastructure.config.MissionMemoryEmbeddingProperties;

import java.time.LocalDateTime;

@Component
public class MissionMemoryEmbeddingBackoffPolicy {

    private final MissionMemoryEmbeddingProperties properties;

    public MissionMemoryEmbeddingBackoffPolicy(MissionMemoryEmbeddingProperties properties) {
        this.properties = properties;
    }

    // 실패 횟수에 따라 재시도 간격을 늘려 provider 장애 시 과호출을 막는다.
    public LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCountAfterFailure) {
        return OutboxBackoffPolicy.nextAttemptAt(
                now,
                attemptCountAfterFailure,
                properties.getRetryInitialDelaySeconds(),
                properties.getRetryMaxDelaySeconds()
        );
    }
}
