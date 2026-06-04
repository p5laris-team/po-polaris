package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 별친구 대화 생성에 사용하는 운영 조절값이다.
 *
 * 대화 원문은 저장하지 않으므로, 입력/응답 길이를 호출 직전에 제한해 비용과 노출 위험을 제어한다.
 */
@ConfigurationProperties(prefix = "ai.character-talk")
public class AiCharacterTalkProperties {

    private int maxUserMessageLength = 300;
    private int maxReplyLength = 350;
    private long toolDeadlineMs = 2500;

    public int getMaxUserMessageLength() {
        return maxUserMessageLength;
    }

    public void setMaxUserMessageLength(int maxUserMessageLength) {
        this.maxUserMessageLength = maxUserMessageLength;
    }

    public int getMaxReplyLength() {
        return maxReplyLength;
    }

    public void setMaxReplyLength(int maxReplyLength) {
        this.maxReplyLength = maxReplyLength;
    }

    public long getToolDeadlineMs() {
        return toolDeadlineMs;
    }

    public void setToolDeadlineMs(long toolDeadlineMs) {
        this.toolDeadlineMs = toolDeadlineMs;
    }

    public int normalizedMaxUserMessageLength() {
        return Math.max(1, maxUserMessageLength);
    }

    public int normalizedMaxReplyLength() {
        return Math.max(80, maxReplyLength);
    }

    public long normalizedToolDeadlineMs() {
        return Math.max(300, toolDeadlineMs);
    }

}
