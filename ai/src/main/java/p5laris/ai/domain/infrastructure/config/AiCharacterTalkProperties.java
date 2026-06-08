package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 별친구 대화 생성에 사용하는 운영 조절값이다.
 *
 * 원문 대화는 짧은 기간만 보관하고, 오래된 대화는 요약/embedding 기억으로 전환해 비용과 노출 범위를 제어한다.
 */
@ConfigurationProperties(prefix = "ai.character-talk")
public class AiCharacterTalkProperties {

    private int maxUserMessageLength = 300;
    private int maxReplyLength = 350;
    private long toolDeadlineMs = 2500;
    private int sessionTtlMinutes = 30;
    private int historyMaxTurns = 6;
    private int memorySearchTopK = 3;
    private double memorySimilarityThreshold = 0.0d;
    private int messageRetentionHours = 24;
    private int sessionRetentionDays = 30;
    private boolean cleanupEnabled = true;
    private long cleanupFixedDelayMs = 3_600_000L;

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

    public int getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(int sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public int getHistoryMaxTurns() {
        return historyMaxTurns;
    }

    public void setHistoryMaxTurns(int historyMaxTurns) {
        this.historyMaxTurns = historyMaxTurns;
    }

    public int getMemorySearchTopK() {
        return memorySearchTopK;
    }

    public void setMemorySearchTopK(int memorySearchTopK) {
        this.memorySearchTopK = memorySearchTopK;
    }

    public double getMemorySimilarityThreshold() {
        return memorySimilarityThreshold;
    }

    public void setMemorySimilarityThreshold(double memorySimilarityThreshold) {
        this.memorySimilarityThreshold = memorySimilarityThreshold;
    }

    public int getMessageRetentionHours() {
        return messageRetentionHours;
    }

    public void setMessageRetentionHours(int messageRetentionHours) {
        this.messageRetentionHours = messageRetentionHours;
    }

    public int getSessionRetentionDays() {
        return sessionRetentionDays;
    }

    public void setSessionRetentionDays(int sessionRetentionDays) {
        this.sessionRetentionDays = sessionRetentionDays;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public long getCleanupFixedDelayMs() {
        return cleanupFixedDelayMs;
    }

    public void setCleanupFixedDelayMs(long cleanupFixedDelayMs) {
        this.cleanupFixedDelayMs = cleanupFixedDelayMs;
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

    public int normalizedSessionTtlMinutes() {
        return Math.max(5, sessionTtlMinutes);
    }

    public int normalizedHistoryMaxTurns() {
        return Math.max(1, historyMaxTurns);
    }

    public int normalizedMemorySearchTopK() {
        return Math.max(0, Math.min(10, memorySearchTopK));
    }

    public double normalizedMemorySimilarityThreshold() {
        return Math.max(0.0d, Math.min(1.0d, memorySimilarityThreshold));
    }

    public int normalizedMessageRetentionHours() {
        return Math.max(1, messageRetentionHours);
    }

    public int normalizedSessionRetentionDays() {
        return Math.max(1, sessionRetentionDays);
    }

    public long normalizedCleanupFixedDelayMs() {
        return Math.max(60_000L, cleanupFixedDelayMs);
    }

}
