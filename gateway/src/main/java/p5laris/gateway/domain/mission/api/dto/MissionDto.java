package p5laris.gateway.domain.mission.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * mission REST API에서 사용하는 요청/응답 DTO 모음이다.
 *
 * <p>gateway 바깥의 클라이언트는 protobuf 구조를 몰라도 되므로,
 * 이 클래스의 DTO만 보고 REST 응답 형태를 이해할 수 있게 분리한다.</p>
 */
public class MissionDto {

    /**
     * 현재 미션 조회와 다음 미션 생성 응답에서 공통으로 사용하는 미션 화면용 데이터다.
     */
    public record MissionResponse(
            Long id,
            String missionDate,
            Integer stackOrder,
            String title,
            String description,
            String characterMessage,
            String category,
            String difficulty,
            Integer rewardStarPiece,
            String status
    ) {
    }

    /**
     * 오늘 제안된 미션 stack과 화면 표시용 집계 정보를 함께 반환한다.
     */
    public record TodayMissionsResponse(
            String missionDate,
            Integer maxDailyOffers,
            Integer offeredCount,
            Integer completedCount,
            Integer rejectedCount,
            Integer remainingOfferCount,
            Integer maxDailyRewardCount,
            Integer completedRewardCount,
            Integer remainingRewardCount,
            Integer maxDailyRejectCount,
            Integer remainingRejectCount,
            Long currentMissionId,
            List<TodayMissionItem> missions
    ) {
    }

    /**
     * 오늘 미션 히스토리 목록의 단일 항목이다.
     *
     * <p>답변 전문은 민감할 수 있어 목록 응답에는 포함하지 않는다.</p>
     */
    public record TodayMissionItem(
            Long id,
            Integer stackOrder,
            String title,
            String category,
            String difficulty,
            Integer rewardStarPiece,
            String status,
            String characterMessage,
            String createdAt,
            String completedAt,
            String rejectedAt,
            String completionQuestion,
            String answerPreview,
            Boolean hasAnswer
    ) {
    }

    /**
     * 미션 상세 화면에서 완료 질문과 답변 전문까지 보여주기 위한 응답이다.
     */
    public record MissionDetailResponse(
            Long id,
            String missionDate,
            Integer stackOrder,
            String title,
            String description,
            String characterMessage,
            String category,
            String difficulty,
            Integer rewardStarPiece,
            String status,
            String createdAt,
            String completedAt,
            String rejectedAt,
            CompletionQuestion question,
            CompletionAnswer answer,
            String completionCharacterResponse,
            Boolean hasAnswer,
            MissionSatisfactionFeedback satisfactionFeedback
    ) {
    }

    /**
     * 완료 미션에 사용자가 이미 남긴 만족도 피드백 요약이다.
     */
    public record MissionSatisfactionFeedback(
            String reaction,
            String updatedAt
    ) {
    }

    /**
     * 다음 미션 생성을 요청할 때 필요한 값이다.
     *
     * <p>characterId는 미션을 "어떤 캐릭터가 제안했는지" 기록하기 위한 값이고,
     * 현재 미션 중복 여부나 소유권 판단은 로그인한 userId 기준으로 처리한다.</p>
     */
    public record CreateNextMissionRequest(
            @NotNull
            @Positive
            Long characterId,

            @PositiveOrZero
            Long lastMissionId
    ) {
    }

    /**
     * 미션을 거절했을 때 클라이언트가 화면 상태를 갱신하는 데 필요한 응답이다.
     */
    public record RejectMissionResponse(
            Long missionId,
            String status,
            String rejectedAt,
            String characterMessage
    ) {
    }

    /**
     * 미션 거절 이유는 선택 입력이다. 비어 있으면 mission 서버가 JUST_SKIP으로 저장한다.
     */
    public record RejectMissionRequest(
            String reasonCode,

            @Size(max = 100)
            String reasonText
    ) {
    }

    /**
     * 완료 버튼을 눌렀을 때 생성되거나 재사용되는 완료 질문 세션 응답이다.
     */
    public record CompletionSessionResponse(
            Long missionId,
            String status,
            CompletionQuestion question
    ) {
    }

    /**
     * 사용자가 미션 완료를 증명하기 위해 답해야 하는 질문 1개를 표현한다.
     */
    public record CompletionQuestion(
            Long id,
            String text,
            String inputType,
            Integer minLength,
            Integer maxLength
    ) {
    }

    /**
     * 완료 질문 답변 제출 요청이다.
     */
    public record SubmitCompletionAnswerRequest(
            @NotBlank
            @Size(max = 300)
            String answer
    ) {
    }

    /**
     * 답변 저장, 미션 완료, 보상 지급까지 끝난 뒤 반환하는 결과 응답이다.
     */
    public record CompletionAnswerResponse(
            Long missionId,
            String status,
            CompletionAnswer answer,
            MissionReward reward,
            WalletSnapshot wallet,
            String rewardStatus,
            CharacterExp characterExp,
            String characterMessage
    ) {
    }

    /**
     * 미션 완료로 캐릭터 성장에 반영된 경험치 상태다.
     */
    public record CharacterExp(
            Integer expAmount,
            Integer expGained,
            Boolean levelUp,
            String status,
            CharacterGrowth beforeGrowth,
            CharacterGrowth afterGrowth
    ) {
    }

    /**
     * 캐릭터 성장 스냅샷이다.
     */
    public record CharacterGrowth(
            Integer level,
            Integer exp,
            Integer currentLevelExp,
            Integer nextLevelExp,
            Integer expToNextLevel,
            Integer progressPercent,
            String growthStage,
            String growthStageLabel,
            Boolean maxLevel
    ) {
    }

    /**
     * 거절 이유 또는 완료 만족도 피드백 저장 요청이다.
     */
    public record UpsertMissionFeedbackRequest(
            @NotBlank
            String feedbackType,

            String reaction,

            String reasonCode,

            @Size(max = 100)
            String reasonText
    ) {
    }

    /**
     * 저장된 미션 피드백 요약이다.
     */
    public record MissionFeedbackResponse(
            Long missionId,
            String feedbackType,
            String reaction,
            String reasonCode,
            String reasonText,
            String updatedAt
    ) {
    }

    /**
     * 사용자가 제출한 답변과 저장 시각이다.
     */
    public record CompletionAnswer(
            String text,
            String answeredAt
    ) {
    }

    /**
     * 이번 미션 완료로 지급된 보상량이다.
     */
    public record MissionReward(
            Integer starPiece,
            Integer affection
    ) {
    }

    /**
     * 보상 반영 후 지갑 상태 일부를 보여주는 스냅샷이다.
     */
    public record WalletSnapshot(
            Integer starPiece
    ) {
    }
}
