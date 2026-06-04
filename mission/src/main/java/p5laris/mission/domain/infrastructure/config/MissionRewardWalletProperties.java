package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mission.reward-wallet")
public class MissionRewardWalletProperties {

    /*
     * 사용자가 미션 완료 결과 화면에서 wallet 모듈 응답을 오래 기다리지 않도록 하는 제한시간이다.
     *
     * 제한시간 안에 지급이 끝나면 PAID와 현재 별조각을 바로 보여주고,
     * 제한시간을 넘기면 outbox 재처리에 맡긴 뒤 PENDING 응답으로 전환한다.
     */
    private long deadlineMs;

    public long getDeadlineMs() {
        return Math.max(100L, deadlineMs);
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }
}
