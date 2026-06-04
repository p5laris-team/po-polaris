package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mission.character-exp")
public class MissionCharacterExpProperties {

    /*
     * 미션 완료 응답이 character 모듈을 오래 기다리지 않도록 하는 제한시간이다.
     *
     * 제한시간 안에 경험치가 반영되면 성장 스냅샷을 바로 내려주고,
     * 실패하거나 지연되면 mission outbox 재처리에 맡긴 뒤 PENDING 응답으로 전환한다.
     */
    private long deadlineMs;

    public long getDeadlineMs() {
        return Math.max(100L, deadlineMs);
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }
}
