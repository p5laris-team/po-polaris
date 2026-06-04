package p5laris.gateway.domain.character.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 별친구 대화의 사용자별 일일 사용 제한 설정이다.
 *
 * provider rate limit은 비용 폭주 방어용이고, 이 설정은 프론트 대화 UI에서 보여줄
 * "오늘 남은 대화 횟수" 계약을 위한 상품 정책이다.
 */
@Component
@ConfigurationProperties(prefix = "gateway.character-talk.daily-limit")
public class CharacterTalkDailyLimitProperties {

    private boolean enabled = true;
    private String backend = "redis";
    private int dailyLimit = 20;
    private boolean failClosed = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public boolean isRedisBackend() {
        return "redis".equalsIgnoreCase(backend);
    }
}
