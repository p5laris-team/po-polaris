package p5laris.mission.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.common.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_templates")
public class MissionTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_title", nullable = false, length = 100)
    private String baseTitle;

    @Column(name = "base_description", nullable = false, columnDefinition = "text")
    private String baseDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MissionCategoryType category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MissionDifficultyType difficulty;

    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "fallback_character_message", nullable = false, columnDefinition = "text")
    private String fallbackCharacterMessage;

    @Column(name = "fallback_question", nullable = false, columnDefinition = "text")
    private String fallbackQuestion;

    @Column(name = "fallback_completion_response", nullable = false, columnDefinition = "text")
    private String fallbackCompletionResponse;
}
