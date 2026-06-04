package p5laris.item.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * item 모듈이 user(wallet) 모듈을 동기로 호출할 때 적용할 제한시간 설정입니다.
 */
@Component
@ConfigurationProperties(prefix = "item.purchase-wallet")
public class ItemPurchaseWalletProperties {

    private long deadlineMs;

    public long getDeadlineMs() {
        return Math.max(100L, deadlineMs);
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }
}
