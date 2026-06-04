package p5laris.gateway.domain.character.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 별친구 대화에서 AI gRPC 호출에 적용할 설정이다.
 *
 * 공통 gRPC deadline은 AiService를 빠른 실패 대상으로 보지만,
 * 별친구 대화는 SSE 연결 안에서 fallback을 보장하므로 이 endpoint 전용 deadline을 별도로 둔다.
 */
@Component
@ConfigurationProperties(prefix = "gateway.character-talk.ai")
public class CharacterTalkAiProperties {

    private long deadlineMs = 8000;

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    public long normalizedDeadlineMs() {
        return Math.max(1000, deadlineMs);
    }
}
