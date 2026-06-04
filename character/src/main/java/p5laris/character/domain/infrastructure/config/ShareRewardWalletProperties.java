package p5laris.character.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "character.share-reward-wallet")
public class ShareRewardWalletProperties {

    /*
     * 공유 완료 직후 wallet 모듈 응답을 오래 기다리지 않도록 하는 제한시간이다.
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
