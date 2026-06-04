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
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_missions")
public class UserMission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "mission_template_id")
    private Long missionTemplateId;

    @Column(name = "ai_generation_id")
    private Long aiGenerationId;

    @Column(name = "mission_date", nullable = false)
    private LocalDate missionDate;

    @Column(name = "stack_order", nullable = false)
    private int stackOrder;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "character_message", nullable = false, columnDefinition = "text")
    private String characterMessage;

    @Column(name = "completion_character_response", columnDefinition = "text")
    private String completionCharacterResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MissionCategoryType category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MissionDifficultyType difficulty;

    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserMissionStatus status;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "completion_started_at")
    private LocalDateTime completionStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /**
     * seed 미션 템플릿을 실제 유저 미션으로 복사해 생성한다.
     *
     * MissionTemplate은 공통 원본이고, UserMission은 특정 유저가 특정 날짜에 받은 실제 미션 기록이다.
     * characterId는 현재 미션 조회 조건이 아니라 "이 미션을 어떤 캐릭터가 제안했는지" 남기는 값이다.
     */
    public static UserMission offerFromTemplate(
            Long userId,
            Long characterId,
            LocalDate missionDate,
            int stackOrder,
            MissionTemplate template,
            LocalDateTime now
    ) {
        UserMission mission = new UserMission();
        mission.userId = userId;
        mission.characterId = characterId;
        mission.missionTemplateId = template.getId();
        mission.missionDate = missionDate;
        mission.stackOrder = stackOrder;
        mission.title = template.getBaseTitle();
        mission.description = template.getBaseDescription();
        mission.characterMessage = template.getFallbackCharacterMessage();
        mission.completionCharacterResponse = template.getFallbackCompletionResponse();
        mission.category = template.getCategory();
        mission.difficulty = template.getDifficulty();
        mission.rewardStarPiece = template.getRewardStarPiece();
        mission.status = UserMissionStatus.OFFERED;
        mission.offeredAt = now;
        return mission;
    }

    /**
     * 검증을 통과한 AI 자율 미션 후보를 실제 유저 미션에 반영한다.
     *
     * 처음에는 seed template fallback row로 저장하지만,
     * AI 후보가 안전 검증을 통과하면 template 연결을 끊고 AI가 만든 제목/설명/문구를 사용한다.
     * 보상은 AI가 아니라 mission 서비스의 난이도별 보상 정책으로 계산된 값만 받는다.
     */
    public void applyGeneratedMission(
            Long aiGenerationId,
            String title,
            String description,
            String characterMessage,
            String completionCharacterResponse,
            MissionCategoryType category,
            MissionDifficultyType difficulty,
            int rewardStarPiece
    ) {
        this.missionTemplateId = null;
        this.aiGenerationId = aiGenerationId;
        this.title = title;
        this.description = description;
        this.characterMessage = characterMessage;
        this.completionCharacterResponse = completionCharacterResponse;
        this.category = category;
        this.difficulty = difficulty;
        this.rewardStarPiece = rewardStarPiece;
    }

    // OFFERED/ANSWERING 상태 미션을 REJECTED로 바꾸고 거절 시각을 기록한다.
    public void reject(LocalDateTime rejectedAt) {
        this.status = UserMissionStatus.REJECTED;
        this.rejectedAt = rejectedAt;
    }

    // OFFERED 상태 미션을 완료 질문 답변 중인 ANSWERING 상태로 바꾼다.
    public void startAnswering(LocalDateTime completionStartedAt) {
        this.status = UserMissionStatus.ANSWERING;
        this.completionStartedAt = completionStartedAt;
    }

    // 답변이 제출된 미션을 COMPLETED 상태로 바꾸고 완료 시각을 기록한다.
    public void complete(LocalDateTime completedAt) {
        this.status = UserMissionStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    // wallet 보상 지급까지 성공한 뒤, 같은 미션에 보상이 다시 지급되지 않도록 멱등키를 남긴다.
    public void recordRewardPaid(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    // idempotency_key가 있으면 mission 관점에서는 보상 지급이 완료된 미션으로 본다.
    public boolean isRewardPaid() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    // 현재 미션이 거절 가능한 제안 상태인지 확인한다.
    public boolean isOffered() {
        return status == UserMissionStatus.OFFERED;
    }

    // 완료 질문 세션이 이미 시작된 상태인지 확인한다.
    public boolean isAnswering() {
        return status == UserMissionStatus.ANSWERING;
    }

    // 이미 완료 처리된 미션인지 확인한다.
    public boolean isCompleted() {
        return status == UserMissionStatus.COMPLETED;
    }
}
