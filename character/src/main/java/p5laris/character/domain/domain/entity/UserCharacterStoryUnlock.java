package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * 유저가 특정 별친구로 해금한 기억 조각 기록이다.
 *
 * 모든 터치 로그를 저장하지 않고, LORE/EASTER_EGG를 처음 발견한 사실만 저장한다.
 * 이 기록은 나중에 프론트 기억 조각 목록과 AI 대화 context의 기반 데이터로 사용된다.
 */
@Entity
@Table(
        name = "user_character_story_unlocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_character_story_unlocks_fragment",
                        columnNames = {"user_id", "user_character_id", "story_fragment_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCharacterStoryUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_character_id", nullable = false)
    private Long userCharacterId;

    @Column(name = "story_fragment_id", nullable = false)
    private Long storyFragmentId;

    /** 목록/응답에서 조각을 식별하기 쉽게 원본 memory_key를 함께 보관한다. */
    @Column(name = "memory_key", nullable = false, length = 120)
    private String memoryKey;

    /** 해금 당시 캐릭터 레벨. 이후 성장하더라도 처음 발견한 시점을 보존한다. */
    @Column(name = "unlocked_level", nullable = false)
    private int unlockedLevel;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private UserCharacterStoryUnlock(
            Long userId,
            Long userCharacterId,
            Long storyFragmentId,
            String memoryKey,
            int unlockedLevel
    ) {
        this.userId = userId;
        this.userCharacterId = userCharacterId;
        this.storyFragmentId = storyFragmentId;
        this.memoryKey = memoryKey;
        this.unlockedLevel = unlockedLevel;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.unlockedAt = now;
        this.createdAt = now;
    }
}
