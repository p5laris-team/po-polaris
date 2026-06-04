package p5laris.gateway.domain.character.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 별친구 대화 SSE 연결 설정이다.
 *
 * AI 실패는 fallback 문장으로 처리하므로, 여기 timeout은 브라우저 연결이 과하게 오래 붙어 있지 않게 하는 안전장치다.
 */
@Component
@ConfigurationProperties(prefix = "gateway.character-talk.sse")
public class CharacterTalkSseProperties {

    private long timeoutMs = 30000;

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long normalizedTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : 30000;
    }
}
