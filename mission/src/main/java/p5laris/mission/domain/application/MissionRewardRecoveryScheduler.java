package p5laris.mission.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

/**
 * 실패한 미션 보상 outbox를 주기적으로 다시 발송하는 스케줄러다.
 *
 * 평소 사용자가 미션을 완료할 때는 즉시 지급을 먼저 시도한다.
 * 이 스케줄러는 그 즉시 지급이 네트워크 문제 등으로 실패해 PENDING으로 남은 건만 복구한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionRewardRecoveryScheduler {

    private final MissionRewardDispatcher missionRewardDispatcher;
    private final MissionRewardOutboxProperties missionRewardOutboxProperties;

    @Scheduled(
            fixedDelayString = "${mission.reward-outbox.fixed-delay-ms}",
            initialDelayString = "${mission.reward-outbox.initial-delay-ms}"
    )
    public void recoverMissionRewards() {
        if (!missionRewardOutboxProperties.isEnabled()) {
            return;
        }

        int succeededCount = missionRewardDispatcher.dispatchDue(missionRewardOutboxProperties.getBatchSize());
        if (succeededCount > 0) {
            log.info("미션 보상 outbox 재처리 완료. 성공건수={}", succeededCount);
        }
    }
}
