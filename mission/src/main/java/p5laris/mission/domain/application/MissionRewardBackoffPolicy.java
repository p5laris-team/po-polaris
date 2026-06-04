package p5laris.mission.domain.application;

import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

import java.time.LocalDateTime;

@Component
public class MissionRewardBackoffPolicy {

    private final MissionRewardOutboxProperties missionRewardOutboxProperties;

    public MissionRewardBackoffPolicy(MissionRewardOutboxProperties missionRewardOutboxProperties) {
        this.missionRewardOutboxProperties = missionRewardOutboxProperties;
    }

    /*
     * 실패 횟수에 따라 1분, 2분, 4분처럼 지수적으로 재시도 간격을 늘린다.
     * 최초/최대 지연 시간은 운영 상황에 맞춰 env로 조절한다.
     */
    public LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCountAfterFailure) {
        int safeAttemptCount = Math.max(1, attemptCountAfterFailure);
        int exponent = Math.min(safeAttemptCount - 1, 5);
        long delaySeconds = Math.min(
                missionRewardOutboxProperties.getRetryMaxDelaySeconds(),
                missionRewardOutboxProperties.getRetryInitialDelaySeconds() * (1L << exponent)
        );
        return now.plusSeconds(delaySeconds);
    }
}
