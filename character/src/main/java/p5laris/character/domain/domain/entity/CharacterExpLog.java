package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.character.domain.domain.enums.CharacterExpSourceType;

import java.time.Instant;

/**
 * 캐릭터 경험치 지급 로그다.
 *
 * 미션 outbox 재처리나 네트워크 재시도로 같은 source가 다시 들어와도
 * 캐릭터 모듈이 최종적으로 중복 경험치 지급을 막기 위해 저장한다.
 */
@Entity
@Table(
        name = "character_exp_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_character_exp_logs_idempotency_key", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_character_exp_logs_source", columnNames = {"source_type", "source_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterExpLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private CharacterExpSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "exp_amount", nullable = false)
    private int expAmount;

    @Column(name = "before_exp", nullable = false)
    private int beforeExp;

    @Column(name = "after_exp", nullable = false)
    private int afterExp;

    @Column(name = "before_level", nullable = false)
    private int beforeLevel;

    @Column(name = "after_level", nullable = false)
    private int afterLevel;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private CharacterExpLog(
            Long userId,
            Long characterId,
            CharacterExpSourceType sourceType,
            Long sourceId,
            String idempotencyKey,
            int expAmount,
            int beforeExp,
            int afterExp,
            int beforeLevel,
            int afterLevel
    ) {
        this.userId = userId;
        this.characterId = characterId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.idempotencyKey = idempotencyKey;
        this.expAmount = expAmount;
        this.beforeExp = beforeExp;
        this.afterExp = afterExp;
        this.beforeLevel = beforeLevel;
        this.afterLevel = afterLevel;
    }

    public boolean matches(Long userId, Long characterId, CharacterExpSourceType sourceType, Long sourceId, String idempotencyKey) {
        return this.userId.equals(userId)
                && this.characterId.equals(characterId)
                && this.sourceType == sourceType
                && this.sourceId.equals(sourceId)
                && this.idempotencyKey.equals(idempotencyKey);
    }

    public boolean matchesSource(Long userId, Long characterId, CharacterExpSourceType sourceType, Long sourceId) {
        return this.userId.equals(userId)
                && this.characterId.equals(characterId)
                && this.sourceType == sourceType
                && this.sourceId.equals(sourceId);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
