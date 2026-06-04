package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.policy.CharacterGrowthPolicy;

import java.time.Instant;

/**
 * ERD 1.4 user_characters
 * 사용자가 생성한 캐릭터를 저장한다.
 *
 * 상태값 정책 (AGENTS.md §20.1):
 * - fullness  : 포만감, 높을수록 좋음 (0~100)
 * - energy    : 에너지, 높을수록 좋음 (0~100)
 * - affection : 애정도, 높을수록 좋음 (0~100)
 *
 * 주의: ERD 초안의 hunger_status 컬럼명은 AGENTS.md 정책과 충돌하므로
 * 정책 문서 우선 원칙(AGENTS.md §3)에 따라 fullness로 대체한다.
 */
@Entity
@Table(
        name = "user_characters",
        indexes = {
                @Index(name = "idx_user_characters_user_id_active", columnList = "user_id, active"),
                @Index(name = "idx_user_characters_character_type_id", columnList = "character_type_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCharacter {

    // ── 정책 상수 (AGENTS.md 및 상태 감소 지연 평가 정책) ──
    private static final int STAT_DECREASE_CYCLE_HOURS = 5;
    private static final int DECREASE_AMOUNT_FULLNESS = 20;
    private static final int DECREASE_AMOUNT_ENERGY = 10;
    private static final int DECREASE_AMOUNT_AFFECTION = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (다른 도메인 직접 참조 금지 → FK만 보유) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_type_id", nullable = false)
    private CharacterType characterType;

    /** 캐릭터 이름 (1~10자) */
    @Column(nullable = false, length = 10)
    private String name;

    /** 레벨 */
    @Column(nullable = false)
    private int level;

    /** 누적 경험치 */
    @Column(nullable = false)
    private int exp;

    /**
     * 포만감 (0~100). AGENTS.md §20.1 fullness.
     * 높을수록 좋은 값. FEED 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int fullness;

    /**
     * 에너지 (0~100). AGENTS.md §20.1 energy.
     * 높을수록 좋은 값. SLEEP 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int energy;

    /**
     * 애정도 (0~100). AGENTS.md §20.1 affection.
     * 높을수록 좋은 값. PLAY 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int affection;

    /** 현재 활성 캐릭터 여부. 사용자당 1개만 true. */
    @Column(nullable = false)
    private boolean active;

    /** 장착한 스킨 아이템 ID (nullable) */
    @Column(name = "equipped_skin_id")
    private Long equippedSkinId;

    /** 마지막으로 상태값이 차감된 시간 (지연 평가용) */
    @Column(name = "last_stat_decreased_at", nullable = false)
    private Instant lastStatDecreasedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private UserCharacter(Long userId, CharacterType characterType, String name,
                          int level, int exp, int fullness, int energy, int affection, boolean active) {
        this.userId = userId;
        this.characterType = characterType;
        this.name = name;
        this.level = level;
        this.exp = exp;
        this.fullness = fullness;
        this.energy = energy;
        this.affection = affection;
        this.active = active;
        this.lastStatDecreasedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── 상태 변경 메서드 ───────────────────────────────────────────────────

    /**
     * 접속/조회 시 시간에 따른 상태 차감 로직 (지연 평가).
     * @return 변경이 발생했으면 true, 아니면 false
     */
    public boolean calculateTimeBasedStatDecrease() {
        Instant now = Instant.now();
        long secondsElapsed = java.time.Duration.between(this.lastStatDecreasedAt, now).getSeconds();
        long hoursElapsed = secondsElapsed / 3600;
        long cycles = hoursElapsed / STAT_DECREASE_CYCLE_HOURS;

        if (cycles > 0) {
            this.fullness = Math.max(0, this.fullness - (int)(cycles * DECREASE_AMOUNT_FULLNESS));
            this.energy = Math.max(0, this.energy - (int)(cycles * DECREASE_AMOUNT_ENERGY));
            this.affection = Math.max(0, this.affection - (int)(cycles * DECREASE_AMOUNT_AFFECTION));
            
            // 처리한 사이클만큼만 시간 증가 (나머지 자투리 시간 보존)
            this.lastStatDecreasedAt = this.lastStatDecreasedAt.plusSeconds(cycles * STAT_DECREASE_CYCLE_HOURS * 3600);
            this.updatedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 돌봄 액션 적용. ActionType에 따라 대응 상태를 amount만큼 회복한다.
     * 상태는 100을 초과하지 않는다 (AGENTS.md §20.2).
     */
    public void applyCare(ActionType actionType, int amount) {
        // 돌봄 액션 전에 밀린 시간 기반 차감 먼저 처리
        calculateTimeBasedStatDecrease();
        
        switch (actionType) {
            case FEED  -> this.fullness  = Math.min(100, this.fullness  + amount);
            case SLEEP -> this.energy    = Math.min(100, this.energy    + amount);
            case PLAY  -> this.affection = Math.min(100, this.affection + amount);
        }
        this.updatedAt = Instant.now();
    }

    public void gainExp(int amount) {
        if (amount <= 0) {
            return;
        }
        this.exp += amount;
        this.level = calculateLevel(this.exp);
        this.updatedAt = Instant.now();
    }

    /**
     * 캐릭터 성장 정책 기준 경험치별 레벨업 테이블.
     * Lv.1: 0~199
     * Lv.2: 200~599
     * Lv.3: 600 이상 (만렙)
     */
    private int calculateLevel(int currentExp) {
        return CharacterGrowthPolicy.calculateLevel(currentExp);
    }

    public void equipSkin(Long skinId) {
        this.equippedSkinId = skinId;
        this.updatedAt = Instant.now();
    }

    public void unequipSkin() {
        this.equippedSkinId = null;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void updateName(String newName) {
        if (newName == null || newName.trim().isEmpty() || newName.length() > 10) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.INVALID_CHARACTER_NAME);
        }
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    /**
     * 캐릭터의 현재 상태 수치(포만감, 에너지, 애정도)에 근거하여 기분(Mood)을 동적으로 계산합니다.
     * 우선순위: HUNGRY (포만감 < 40) > LOW_ENERGY (에너지 < 40) > LONELY (애정도 < 40) > IDLE (그 외)
     */
    public CharacterMood calculateMood() {
        if (this.fullness < 40) {
            return CharacterMood.HUNGRY;
        }
        if (this.energy < 40) {
            return CharacterMood.LOW_ENERGY;
        }
        if (this.affection < 40) {
            return CharacterMood.LONELY;
        }
        return CharacterMood.IDLE;
    }
}
