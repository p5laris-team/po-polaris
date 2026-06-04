package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.character.domain.domain.enums.CharacterStoryFragmentType;
import p5laris.character.domain.domain.enums.CharacterStoryTriggerType;

import java.time.Instant;

/**
 * 별친구의 성장 서사 원본 조각이다.
 *
 * 캐릭터를 터치하거나 특정 상태 트리거가 발생했을 때 이 테이블에서 후보를 고른다.
 * COMMON은 일반 대사용, LORE/EASTER_EGG는 유저별 해금 기록을 남기는 기억 조각으로 사용한다.
 */
@Entity
@Table(
        name = "character_story_fragments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_character_story_fragments_memory_key", columnNames = "memory_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterStoryFragment {

    /** 특정 캐릭터가 아니라 모든 별친구에게 공통으로 노출 가능한 세계관 조각 코드 */
    public static final String COMMON_CHARACTER_TYPE_CODE = "COMMON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_key", nullable = false, length = 120)
    private String memoryKey;

    /** MUMU / NOVA / JJORY / COMMON */
    @Column(name = "character_type_code", nullable = false, length = 30)
    private String characterTypeCode;

    /** 이 레벨 이상에서만 후보가 된다. */
    @Column(name = "min_level", nullable = false)
    private int minLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fragment_type", nullable = false, length = 30)
    private CharacterStoryFragmentType fragmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private CharacterStoryTriggerType triggerType;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false, length = 500)
    private String interpretation;

    @Column(name = "story_text", nullable = false, columnDefinition = "TEXT")
    private String storyText;

    /** 같은 조건의 후보 중 노출 우선순위를 안정적으로 정하기 위한 값 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private CharacterStoryFragment(
            String memoryKey,
            String characterTypeCode,
            int minLevel,
            CharacterStoryFragmentType fragmentType,
            CharacterStoryTriggerType triggerType,
            String title,
            String message,
            String interpretation,
            String storyText,
            int sortOrder,
            boolean active
    ) {
        this.memoryKey = memoryKey;
        this.characterTypeCode = characterTypeCode;
        this.minLevel = minLevel;
        this.fragmentType = fragmentType;
        this.triggerType = triggerType;
        this.title = title;
        this.message = message;
        this.interpretation = interpretation;
        this.storyText = storyText;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public boolean unlockable() {
        return fragmentType.unlockable();
    }

    /** 서비스에서 한 번 더 레벨 조건을 방어적으로 확인한다. */
    public boolean forCurrentLevel(int level) {
        return minLevel <= level;
    }

    /** 현재 캐릭터 전용 조각이거나 COMMON 조각이면 후보로 사용할 수 있다. */
    public boolean forCharacter(String currentCharacterTypeCode) {
        return COMMON_CHARACTER_TYPE_CODE.equals(characterTypeCode)
                || characterTypeCode.equals(currentCharacterTypeCode);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
