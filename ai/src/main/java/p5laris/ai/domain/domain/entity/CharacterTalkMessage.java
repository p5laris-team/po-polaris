package p5laris.ai.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;

import java.time.LocalDateTime;

/**
 * 별친구 대화 세션 안의 사용자/assistant 메시지다.
 *
 * 최근 N턴 prompt window를 만들기 위한 원문이며, 장기 기억은 별도 요약 테이블에만 남긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "character_talk_messages")
public class CharacterTalkMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private CharacterTalkSession session;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CharacterTalkMessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "request_id", nullable = false, length = 120)
    private String requestId;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static CharacterTalkMessage create(
            CharacterTalkSession session,
            CharacterTalkMessageRole role,
            String content,
            int sequence,
            String requestId,
            boolean fallbackUsed
    ) {
        CharacterTalkMessage message = new CharacterTalkMessage();
        message.session = session;
        message.userId = session.getUserId();
        message.characterId = session.getCharacterId();
        message.role = role;
        message.content = content;
        message.sequence = sequence;
        message.requestId = requestId;
        message.fallbackUsed = fallbackUsed;
        return message;
    }
}
