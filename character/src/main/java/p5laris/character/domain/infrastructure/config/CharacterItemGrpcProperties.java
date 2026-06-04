package p5laris.character.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * character 모듈이 item 모듈을 동기로 호출할 때 적용할 제한시간 설정이다.
 *
 * 돌보기는 즉시 성공/실패가 중요한 상호작용이므로 item 모듈이 느릴 때 DB 트랜잭션을 오래 점유하지 않게 한다.
 */
@Component
@ConfigurationProperties(prefix = "character.item-grpc")
public class CharacterItemGrpcProperties {

    private long deadlineMs;

    public long getDeadlineMs() {
        return Math.max(100L, deadlineMs);
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }
}
