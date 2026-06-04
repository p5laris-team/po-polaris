package p5laris.gateway.domain.character.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 별친구 대화 prompt에 넣을 캐릭터 context 조회 범위를 조절한다.
 *
 * 해금 기억 조각을 너무 많이 넣으면 응답 비용과 지연이 커지므로 gateway에서 조회 개수를 제한한다.
 */
@Component
@ConfigurationProperties(prefix = "gateway.character-talk.context")
public class CharacterTalkContextProperties {

    private int memoryLimit = 5;

    public int getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(int memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public int normalizedMemoryLimit() {
        return Math.max(0, Math.min(memoryLimit, 10));
    }
}
