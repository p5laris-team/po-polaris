package p5laris.mission.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

/**
 * 실패한 미션 완료 캐릭터 경험치 outbox를 주기적으로 다시 발송하는 스케줄러다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCharacterExpRecoveryScheduler {

    private final MissionCharacterExpDispatcher missionCharacterExpDispatcher;
    private final MissionRewardOutboxProperties missionRewardOutboxProperties;

    @Scheduled(
            fixedDelayString = "${mission.reward-outbox.fixed-delay-ms}",
            initialDelayString = "${mission.reward-outbox.initial-delay-ms}"
    )
    public void recoverMissionCharacterExp() {
        if (!missionRewardOutboxProperties.isEnabled()) {
            return;
        }

        int succeededCount = missionCharacterExpDispatcher.dispatchDue(missionRewardOutboxProperties.getBatchSize());
        if (succeededCount > 0) {
            log.info("미션 캐릭터 경험치 outbox 재처리 완료. 성공건수={}", succeededCount);
        }
    }
}
