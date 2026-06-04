package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.character.domain.domain.enums.ActionType;

import java.time.Instant;

/**
 * ERD 1.5 character_care_logs
 * 밥 주기(FEED), 재우기(SLEEP), 놀기(PLAY) 등 캐릭터 돌봄 기록을 저장한다.
 *
 * 돌봄 액션 (AGENTS.md §20.2):
 * - FEED  → fullness 회복
 * - SLEEP → energy 회복
 * - PLAY  → affection 회복
 */
@Entity
@Table(
        name = "character_care_logs",
        indexes = {
                @Index(name = "idx_care_logs_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_care_logs_character_id_created_at", columnList = "character_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterCareLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 캐릭터 ID */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** 사용한 소모성 아이템 ID (nullable) */
    @Column(name = "item_id")
    private Long itemId;

    /** 돌봄 액션 유형: FEED / SLEEP / PLAY */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    /** 돌봄 전 상태 snapshot (JSON) */
    @Column(name = "before_state_json", columnDefinition = "TEXT")
    private String beforeStateJson;

    /** 돌봄 후 상태 snapshot (JSON) */
    @Column(name = "after_state_json", columnDefinition = "TEXT")
    private String afterStateJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** 멱등키 (클라이언트 제공, 재시도/중복 요청 방지용) */
    @Column(name = "idempotency_key", unique = true, nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Builder
    private CharacterCareLog(Long userId, Long characterId, Long itemId,
                             ActionType actionType, String beforeStateJson, String afterStateJson,
                             String idempotencyKey) {
        this.userId = userId;
        this.characterId = characterId;
        this.itemId = itemId;
        this.actionType = actionType;
        this.beforeStateJson = beforeStateJson;
        this.afterStateJson = afterStateJson;
        this.createdAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
    }
}
