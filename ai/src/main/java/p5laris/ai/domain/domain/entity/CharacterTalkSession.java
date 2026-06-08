package p5laris.ai.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import p5laris.ai.domain.domain.enums.CharacterTalkSessionStatus;

import java.time.LocalDateTime;

/**
 * 별친구 대화 세션이다.
 *
 * 세션은 멀티턴 맥락과 토큰 누적량의 기준점이며, 원문 대화의 장기 기억 여부와 분리된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "character_talk_sessions")
public class CharacterTalkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 80)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "character_type", nullable = false, length = 30)
    private String characterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CharacterTalkSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "total_actual_prompt_tokens")
    private Integer totalActualPromptTokens;

    @Column(name = "total_actual_completion_tokens")
    private Integer totalActualCompletionTokens;

    @Column(name = "total_actual_tokens")
    private Integer totalActualTokens;

    @Column(name = "summary_created_at")
    private LocalDateTime summaryCreatedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static CharacterTalkSession create(
            String sessionId,
            Long userId,
            Long characterId,
            String characterType,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        CharacterTalkSession session = new CharacterTalkSession();
        session.sessionId = sessionId;
        session.userId = userId;
        session.characterId = characterId;
        session.characterType = characterType;
        session.status = CharacterTalkSessionStatus.ACTIVE;
        session.startedAt = now;
        session.lastMessageAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isOwnedBy(Long userId, Long characterId) {
        return this.userId.equals(userId) && this.characterId.equals(characterId);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return status == CharacterTalkSessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void refresh(LocalDateTime now, LocalDateTime nextExpiresAt) {
        this.status = CharacterTalkSessionStatus.ACTIVE;
        this.lastMessageAt = now;
        this.expiresAt = nextExpiresAt;
    }

    public void markExpired() {
        this.status = CharacterTalkSessionStatus.EXPIRED;
    }

    public void markMemoryReady(LocalDateTime summaryCreatedAt) {
        this.status = CharacterTalkSessionStatus.MEMORY_READY;
        this.summaryCreatedAt = summaryCreatedAt;
    }

    public int nextSequence() {
        return messageCount + 1;
    }

    public void recordUserMessage(LocalDateTime now, LocalDateTime nextExpiresAt) {
        this.messageCount += 1;
        this.lastMessageAt = now;
        this.expiresAt = nextExpiresAt;
    }

    public void recordAssistantMessage(Integer actualPromptTokens, Integer actualCompletionTokens, Integer actualTotalTokens) {
        this.messageCount += 1;
        this.totalActualPromptTokens = addNullable(this.totalActualPromptTokens, actualPromptTokens);
        this.totalActualCompletionTokens = addNullable(this.totalActualCompletionTokens, actualCompletionTokens);
        this.totalActualTokens = addNullable(this.totalActualTokens, actualTotalTokens);
    }

    private Integer addNullable(Integer current, Integer value) {
        if (value == null || value < 0) {
            return current;
        }
        return (current == null ? 0 : current) + value;
    }
}
