package p5laris.mission.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionFeedbackReaction;
import p5laris.mission.domain.domain.enums.MissionFeedbackReasonCode;
import p5laris.mission.domain.domain.enums.MissionFeedbackType;

/**
 * 사용자가 미션을 거절하거나 완료 후 남긴 선택 피드백을 저장한다.
 *
 * 이 데이터는 즉시 보상 조건이 아니라 이후 자율 미션/RAG 개인화에 사용할 입력 신호다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "mission_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_feedbacks_user_mission_type",
                columnNames = {"user_id", "mission_id", "feedback_type"}
        )
)
public class MissionFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private MissionFeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MissionFeedbackReaction reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 50)
    private MissionFeedbackReasonCode reasonCode;

    @Column(name = "reason_text", length = 100)
    private String reasonText;

    public static MissionFeedback rejection(
            UserMission mission,
            MissionFeedbackReasonCode reasonCode,
            String reasonText
    ) {
        MissionFeedback feedback = new MissionFeedback();
        feedback.userId = mission.getUserId();
        feedback.missionId = mission.getId();
        feedback.feedbackType = MissionFeedbackType.REJECTION;
        feedback.reasonCode = reasonCode == null ? MissionFeedbackReasonCode.JUST_SKIP : reasonCode;
        feedback.reasonText = reasonText;
        return feedback;
    }

    public static MissionFeedback satisfaction(
            UserMission mission,
            MissionFeedbackReaction reaction
    ) {
        MissionFeedback feedback = new MissionFeedback();
        feedback.userId = mission.getUserId();
        feedback.missionId = mission.getId();
        feedback.feedbackType = MissionFeedbackType.SATISFACTION;
        feedback.reaction = reaction;
        return feedback;
    }

    public void updateRejection(MissionFeedbackReasonCode reasonCode, String reasonText) {
        this.feedbackType = MissionFeedbackType.REJECTION;
        this.reaction = null;
        this.reasonCode = reasonCode == null ? MissionFeedbackReasonCode.JUST_SKIP : reasonCode;
        this.reasonText = reasonText;
    }

    public void updateSatisfaction(MissionFeedbackReaction reaction) {
        this.feedbackType = MissionFeedbackType.SATISFACTION;
        this.reaction = reaction;
        this.reasonCode = null;
        this.reasonText = null;
    }
}
