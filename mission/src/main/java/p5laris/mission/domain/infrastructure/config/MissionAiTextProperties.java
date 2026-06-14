package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mission.ai-text")
public class MissionAiTextProperties {

    /*
     * AI 미션 후보 생성은 외부 provider 응답을 기다리므로 일반 내부 gRPC보다 긴 제한시간을 둔다.
     * 제한시간 안에 성공하면 user_missions row가 AI 후보로 교체되고, 초과하면 seed fallback을 유지한다.
     */
    private long deadlineMs = 8000L;

    public long getDeadlineMs() {
        return Math.max(1000L, deadlineMs);
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }
}
