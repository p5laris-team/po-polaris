package p5laris.gateway.domain.mission.infrastructure.grpc;

import com.p5laris.proto.mission.v1.CreateNextMissionRequest;
import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.GetCurrentMissionRequest;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetMissionDetailRequest;
import com.p5laris.proto.mission.v1.GetMissionDetailResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsRequest;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.Mission;
import com.p5laris.proto.mission.v1.MissionDetail;
import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.RejectMissionRequest;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionRequest;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerRequest;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import com.p5laris.proto.mission.v1.TodayMission;
import com.p5laris.proto.mission.v1.UpsertMissionFeedbackRequest;
import com.p5laris.proto.mission.v1.UpsertMissionFeedbackResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.exception.MissionGatewayErrorCode;
import p5laris.gateway.domain.mission.exception.MissionGatewayException;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

import java.time.LocalDate;

/**
 * mission gRPC 서버를 호출하고 REST DTO로 변환하는 gateway adapter다.
 *
 * <p>gateway는 DB를 직접 보지 않는다. 그래서 이 클래스는 REST 요청에서 받은 값으로
 * protobuf request를 만들고, mission 서버 응답을 다시 REST 응답 DTO로 바꾸는 역할만 한다.</p>
 */
@Service
public class MissionGatewayService {

    private static final long EMPTY_LAST_MISSION_ID = 0L;
    private static final int MAX_COMPLETION_ANSWER_LENGTH = 300;

    @GrpcClient("mission")
    private MissionServiceGrpc.MissionServiceBlockingStub missionStub;

    /**
     * 현재 미션 조회는 userId만 기준으로 한다.
     */
    public MissionDto.MissionResponse getCurrentMission(Long userId) {
        try {
            GetCurrentMissionResponse response = missionStub.getCurrentMission(
                    GetCurrentMissionRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            return toMissionResponse(response.getMission());
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 오늘 미션 히스토리 화면에 필요한 stack 목록과 집계 정보를 조회한다.
     */
    public MissionDto.TodayMissionsResponse getTodayMissions(Long userId) {
        return getMissionHistory(userId, null);
    }

    /**
     * 특정 날짜 미션 히스토리 화면에 필요한 stack 목록과 집계 정보를 조회한다.
     */
    public MissionDto.TodayMissionsResponse getMissionHistory(Long userId, LocalDate missionDate) {
        try {
            GetTodayMissionsRequest.Builder request = GetTodayMissionsRequest.newBuilder()
                    .setUserId(userId);
            if (missionDate != null) {
                request.setMissionDate(missionDate.toString());
            }

            GetTodayMissionsResponse response = missionStub.getTodayMissions(
                    request.build()
            );

            return new MissionDto.TodayMissionsResponse(
                    response.getMissionDate(),
                    response.getMaxDailyOffers(),
                    response.getOfferedCount(),
                    response.getCompletedCount(),
                    response.getRejectedCount(),
                    response.getRemainingOfferCount(),
                    response.getMaxDailyRewardCount(),
                    response.getCompletedRewardCount(),
                    response.getRemainingRewardCount(),
                    response.getMaxDailyRejectCount(),
                    response.getRemainingRejectCount(),
                    response.hasCurrentMissionId() ? response.getCurrentMissionId() : null,
                    response.getMissionsList().stream()
                            .map(this::toTodayMissionItem)
                            .toList()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 미션 1개의 상세 정보와 완료 답변 전문을 조회한다.
     */
    public MissionDto.MissionDetailResponse getMissionDetail(Long userId, Long missionId) {
        validateMissionId(missionId);

        try {
            GetMissionDetailResponse response = missionStub.getMissionDetail(
                    GetMissionDetailRequest.newBuilder()
                            .setUserId(userId)
                            .setMissionId(missionId)
                            .build()
            );

            return toMissionDetailResponse(response.getMission());
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 다음 미션 생성은 userId로 소유권을 판단하고, characterId는 제안 캐릭터 기록용으로 전달한다.
     */
    public MissionDto.MissionResponse createNextMission(
            Long userId,
            MissionDto.CreateNextMissionRequest request
    ) {
        validateCreateNextMissionRequest(request);

        try {
            CreateNextMissionResponse response = missionStub.createNextMission(
                    CreateNextMissionRequest.newBuilder()
                            .setUserId(userId)
                            .setCharacterId(request.characterId())
                            .setLastMissionId(toGrpcLastMissionId(request.lastMissionId()))
                            .build()
            );

            return toMissionResponse(response.getMission());
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 미션 거절은 userId + missionId 기준으로 mission 서버에서 다시 검증한다.
     */
    public MissionDto.RejectMissionResponse rejectMission(Long userId, Long missionId) {
        return rejectMission(userId, missionId, null);
    }

    public MissionDto.RejectMissionResponse rejectMission(
            Long userId,
            Long missionId,
            MissionDto.RejectMissionRequest request
    ) {
        validateMissionId(missionId);

        try {
            RejectMissionRequest.Builder grpcRequest = RejectMissionRequest.newBuilder()
                    .setUserId(userId)
                    .setMissionId(missionId);
            if (request != null && request.reasonCode() != null) {
                grpcRequest.setReasonCode(request.reasonCode());
            }
            if (request != null && request.reasonText() != null) {
                grpcRequest.setReasonText(request.reasonText());
            }

            RejectMissionResponse response = missionStub.rejectMission(
                    grpcRequest.build()
            );

            return new MissionDto.RejectMissionResponse(
                    response.getMissionId(),
                    toRestMissionStatus(response.getStatus().name()),
                    response.getRejectedAt(),
                    response.getCharacterMessage()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 미션 거절 이유 또는 완료 만족도 피드백을 mission 서버에 저장한다.
     */
    public MissionDto.MissionFeedbackResponse upsertMissionFeedback(
            Long userId,
            Long missionId,
            MissionDto.UpsertMissionFeedbackRequest request
    ) {
        validateMissionId(missionId);
        validateFeedbackRequest(request);

        try {
            UpsertMissionFeedbackRequest.Builder grpcRequest = UpsertMissionFeedbackRequest.newBuilder()
                    .setUserId(userId)
                    .setMissionId(missionId)
                    .setFeedbackType(toGrpcFeedbackType(request.feedbackType()));
            if (request.reaction() != null && !request.reaction().isBlank()) {
                grpcRequest.setReaction(toGrpcFeedbackReaction(request.reaction()));
            }
            if (request.reasonCode() != null && !request.reasonCode().isBlank()) {
                grpcRequest.setReasonCode(request.reasonCode());
            }
            if (request.reasonText() != null && !request.reasonText().isBlank()) {
                grpcRequest.setReasonText(request.reasonText());
            }

            UpsertMissionFeedbackResponse response = missionStub.upsertMissionFeedback(grpcRequest.build());

            return new MissionDto.MissionFeedbackResponse(
                    response.getMissionId(),
                    toRestFeedbackType(response.getFeedbackType().name()),
                    toRestFeedbackReaction(response.getReaction().name()),
                    emptyToNull(response.getReasonCode()),
                    emptyToNull(response.getReasonText()),
                    emptyToNull(response.getUpdatedAt())
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 완료 질문 세션을 시작한다. 이미 ANSWERING 상태라면 mission 서버가 기존 질문을 반환한다.
     */
    public MissionDto.CompletionSessionResponse startCompletionSession(Long userId, Long missionId) {
        validateMissionId(missionId);

        try {
            StartCompletionSessionResponse response = missionStub.startCompletionSession(
                    StartCompletionSessionRequest.newBuilder()
                            .setUserId(userId)
                            .setMissionId(missionId)
                            .build()
            );

            return new MissionDto.CompletionSessionResponse(
                    response.getMissionId(),
                    toRestMissionStatus(response.getStatus().name()),
                    new MissionDto.CompletionQuestion(
                            response.getQuestion().getId(),
                            response.getQuestion().getText(),
                            toRestCompletionInputType(response.getQuestion().getInputType().name()),
                            response.getQuestion().getMinLength(),
                            response.getQuestion().getMaxLength()
                    )
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 완료 답변을 제출한다. 보상 중복 방지는 mission 서버의 상태 전이와 멱등 처리에서 담당한다.
     */
    public MissionDto.CompletionAnswerResponse submitCompletionAnswer(
            Long userId,
            Long missionId,
            MissionDto.SubmitCompletionAnswerRequest request
    ) {
        validateMissionId(missionId);
        validateCompletionAnswer(request);

        try {
            SubmitCompletionAnswerResponse response = missionStub.submitCompletionAnswer(
                    SubmitCompletionAnswerRequest.newBuilder()
                            .setUserId(userId)
                            .setMissionId(missionId)
                            .setAnswer(request.answer())
                            .build()
            );

            return new MissionDto.CompletionAnswerResponse(
                    response.getMissionId(),
                    toRestMissionStatus(response.getStatus().name()),
                    new MissionDto.CompletionAnswer(
                            response.getAnswer().getText(),
                            response.getAnswer().getAnsweredAt()
                    ),
                    new MissionDto.MissionReward(
                            response.getReward().getStarPiece(),
                            response.getReward().getAffection()
                    ),
                    response.hasWallet()
                            ? new MissionDto.WalletSnapshot(response.getWallet().getStarPiece())
                            : null,
                    toRestMissionRewardStatus(response.getRewardStatus().name()),
                    response.hasCharacterExp()
                            ? toCharacterExp(response.getCharacterExp())
                            : null,
                    response.getCharacterMessage()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    private MissionDto.MissionResponse toMissionResponse(Mission mission) {
        return new MissionDto.MissionResponse(
                mission.getId(),
                mission.getMissionDate(),
                mission.getStackOrder(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getCharacterMessage(),
                toRestMissionCategory(mission.getCategory().name()),
                toRestMissionDifficulty(mission.getDifficulty().name()),
                mission.getRewardStarPiece(),
                toRestMissionStatus(mission.getStatus().name())
        );
    }

    private MissionDto.TodayMissionItem toTodayMissionItem(TodayMission mission) {
        return new MissionDto.TodayMissionItem(
                mission.getId(),
                mission.getStackOrder(),
                mission.getTitle(),
                toRestMissionCategory(mission.getCategory().name()),
                toRestMissionDifficulty(mission.getDifficulty().name()),
                mission.getRewardStarPiece(),
                toRestMissionStatus(mission.getStatus().name()),
                mission.getCharacterMessage(),
                emptyToNull(mission.getCreatedAt()),
                emptyToNull(mission.getCompletedAt()),
                emptyToNull(mission.getRejectedAt()),
                emptyToNull(mission.getCompletionQuestion()),
                emptyToNull(mission.getAnswerPreview()),
                mission.getHasAnswer()
        );
    }

    private MissionDto.MissionDetailResponse toMissionDetailResponse(MissionDetail mission) {
        return new MissionDto.MissionDetailResponse(
                mission.getId(),
                mission.getMissionDate(),
                mission.getStackOrder(),
                mission.getTitle(),
                mission.getDescription(),
                mission.getCharacterMessage(),
                toRestMissionCategory(mission.getCategory().name()),
                toRestMissionDifficulty(mission.getDifficulty().name()),
                mission.getRewardStarPiece(),
                toRestMissionStatus(mission.getStatus().name()),
                emptyToNull(mission.getCreatedAt()),
                emptyToNull(mission.getCompletedAt()),
                emptyToNull(mission.getRejectedAt()),
                toCompletionQuestionOrNull(mission),
                toCompletionAnswerOrNull(mission),
                emptyToNull(mission.getCompletionCharacterResponse()),
                mission.getHasAnswer(),
                toSatisfactionFeedbackOrNull(mission)
        );
    }

    private MissionDto.MissionSatisfactionFeedback toSatisfactionFeedbackOrNull(MissionDetail mission) {
        if (!mission.hasSatisfactionFeedback()) {
            return null;
        }

        return new MissionDto.MissionSatisfactionFeedback(
                toRestFeedbackReaction(mission.getSatisfactionFeedback().getReaction().name()),
                emptyToNull(mission.getSatisfactionFeedback().getUpdatedAt())
        );
    }

    private MissionDto.CompletionQuestion toCompletionQuestionOrNull(MissionDetail mission) {
        if (!mission.hasQuestion()) {
            return null;
        }

        return new MissionDto.CompletionQuestion(
                mission.getQuestion().getId(),
                mission.getQuestion().getText(),
                toRestCompletionInputType(mission.getQuestion().getInputType().name()),
                mission.getQuestion().getMinLength(),
                mission.getQuestion().getMaxLength()
        );
    }

    private MissionDto.CompletionAnswer toCompletionAnswerOrNull(MissionDetail mission) {
        if (!mission.getHasAnswer()) {
            return null;
        }

        return new MissionDto.CompletionAnswer(
                mission.getAnswer().getText(),
                mission.getAnswer().getAnsweredAt()
        );
    }

    private long toGrpcLastMissionId(Long lastMissionId) {
        if (lastMissionId == null) {
            return EMPTY_LAST_MISSION_ID;
        }

        if (lastMissionId < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        return lastMissionId;
    }

    private void validateCreateNextMissionRequest(MissionDto.CreateNextMissionRequest request) {
        if (request == null || request.characterId() == null || request.characterId() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateMissionId(Long missionId) {
        if (missionId == null || missionId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateCompletionAnswer(MissionDto.SubmitCompletionAnswerRequest request) {
        if (request == null || request.answer() == null) {
            throw new MissionGatewayException(MissionGatewayErrorCode.MISSION_ANSWER_INVALID);
        }

        String answer = request.answer();
        if (answer.isBlank() || answer.length() > MAX_COMPLETION_ANSWER_LENGTH) {
            throw new MissionGatewayException(MissionGatewayErrorCode.MISSION_ANSWER_INVALID);
        }
    }

    private void validateFeedbackRequest(MissionDto.UpsertMissionFeedbackRequest request) {
        if (request == null || request.feedbackType() == null || request.feedbackType().isBlank()) {
            throw new MissionGatewayException(MissionGatewayErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private String toRestMissionStatus(String grpcStatus) {
        return removeGrpcPrefix(grpcStatus, "MISSION_STATUS_");
    }

    private String toRestMissionRewardStatus(String grpcStatus) {
        String value = removeGrpcPrefix(grpcStatus, "MISSION_REWARD_STATUS_");
        return "UNSPECIFIED".equals(value) ? null : value;
    }

    private MissionDto.CharacterExp toCharacterExp(com.p5laris.proto.mission.v1.MissionCharacterExp characterExp) {
        return new MissionDto.CharacterExp(
                characterExp.getExpAmount(),
                characterExp.getExpGained(),
                characterExp.getLevelUp(),
                toRestMissionCharacterExpStatus(characterExp.getStatus().name()),
                characterExp.hasBeforeGrowth() ? toCharacterGrowth(characterExp.getBeforeGrowth()) : null,
                characterExp.hasAfterGrowth() ? toCharacterGrowth(characterExp.getAfterGrowth()) : null
        );
    }

    private MissionDto.CharacterGrowth toCharacterGrowth(com.p5laris.proto.character.v1.CharacterGrowth growth) {
        return new MissionDto.CharacterGrowth(
                growth.getLevel(),
                growth.getExp(),
                growth.getCurrentLevelExp(),
                growth.getNextLevelExp(),
                growth.getExpToNextLevel(),
                growth.getProgressPercent(),
                growth.getGrowthStage(),
                growth.getGrowthStageLabel(),
                growth.getMaxLevel()
        );
    }

    private String toRestMissionCharacterExpStatus(String grpcStatus) {
        String value = removeGrpcPrefix(grpcStatus, "MISSION_CHARACTER_EXP_STATUS_");
        return "UNSPECIFIED".equals(value) ? null : value;
    }

    private String toRestMissionCategory(String grpcCategory) {
        return removeGrpcPrefix(grpcCategory, "MISSION_CATEGORY_");
    }

    private String toRestMissionDifficulty(String grpcDifficulty) {
        return removeGrpcPrefix(grpcDifficulty, "MISSION_DIFFICULTY_");
    }

    private String toRestCompletionInputType(String grpcInputType) {
        return removeGrpcPrefix(grpcInputType, "COMPLETION_INPUT_TYPE_");
    }

    private String toRestFeedbackType(String grpcFeedbackType) {
        return removeGrpcPrefix(grpcFeedbackType, "MISSION_FEEDBACK_TYPE_");
    }

    private String toRestFeedbackReaction(String grpcReaction) {
        String value = removeGrpcPrefix(grpcReaction, "MISSION_FEEDBACK_REACTION_");
        return "UNSPECIFIED".equals(value) ? null : value;
    }

    private com.p5laris.proto.mission.v1.MissionFeedbackType toGrpcFeedbackType(String feedbackType) {
        try {
            return com.p5laris.proto.mission.v1.MissionFeedbackType.valueOf(
                    "MISSION_FEEDBACK_TYPE_" + feedbackType.trim()
            );
        } catch (IllegalArgumentException e) {
            throw new MissionGatewayException(MissionGatewayErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private com.p5laris.proto.mission.v1.MissionFeedbackReaction toGrpcFeedbackReaction(String reaction) {
        try {
            return com.p5laris.proto.mission.v1.MissionFeedbackReaction.valueOf(
                    "MISSION_FEEDBACK_REACTION_" + reaction.trim()
            );
        } catch (IllegalArgumentException e) {
            throw new MissionGatewayException(MissionGatewayErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private String removeGrpcPrefix(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) {
            return value;
        }

        return value.substring(prefix.length());
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    /**
     * mission gRPC status를 gateway의 REST 실패 응답 코드로 바꾼다.
     *
     * <p>mission 서버는 내부 ErrorCode를 gRPC Status로 압축해서 넘긴다.
     * 일부 Status는 여러 도메인 오류가 공유하므로 description 문구까지 확인해
     * 클라이언트가 이해할 수 있는 REST 에러 코드로 복원한다.</p>
     */
    private BusinessException toGatewayException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();

        return switch (code) {
            case NOT_FOUND -> new MissionGatewayException(toNotFoundErrorCode(description));
            case INVALID_ARGUMENT -> new MissionGatewayException(toInvalidArgumentErrorCode(description));
            case ALREADY_EXISTS -> new MissionGatewayException(MissionGatewayErrorCode.MISSION_ALREADY_COMPLETED);
            case RESOURCE_EXHAUSTED -> new MissionGatewayException(toResourceExhaustedErrorCode(description));
            case FAILED_PRECONDITION -> new MissionGatewayException(toFailedPreconditionErrorCode(description));
            case UNAVAILABLE -> new MissionGatewayException(toUnavailableErrorCode(description));
            default -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        };
    }

    private MissionGatewayErrorCode toInvalidArgumentErrorCode(String description) {
        if (contains(description, "MISSION_FEEDBACK_INVALID") || contains(description, "피드백")) {
            return MissionGatewayErrorCode.MISSION_FEEDBACK_INVALID;
        }

        return MissionGatewayErrorCode.MISSION_ANSWER_INVALID;
    }

    private MissionGatewayErrorCode toResourceExhaustedErrorCode(String description) {
        if (contains(description, "MISSION_REJECT_LIMIT_EXCEEDED") || contains(description, "거절")) {
            return MissionGatewayErrorCode.MISSION_REJECT_LIMIT_EXCEEDED;
        }

        return MissionGatewayErrorCode.MISSION_DAILY_LIMIT_EXCEEDED;
    }

    private MissionGatewayErrorCode toNotFoundErrorCode(String description) {
        if (contains(description, "MISSION_TEMPLATE_NOT_FOUND") || contains(description, "템플릿")) {
            return MissionGatewayErrorCode.MISSION_TEMPLATE_NOT_FOUND;
        }

        return MissionGatewayErrorCode.MISSION_NOT_FOUND;
    }

    private MissionGatewayErrorCode toFailedPreconditionErrorCode(String description) {
        if (contains(description, "MISSION_ACTIVE_ALREADY_EXISTS") || contains(description, "진행 중")) {
            return MissionGatewayErrorCode.MISSION_ACTIVE_ALREADY_EXISTS;
        }

        return MissionGatewayErrorCode.MISSION_INVALID_STATUS;
    }

    private MissionGatewayErrorCode toUnavailableErrorCode(String description) {
        if (contains(description, "MISSION_REWARD_FAILED") || contains(description, "보상")) {
            return MissionGatewayErrorCode.MISSION_REWARD_FAILED;
        }

        return MissionGatewayErrorCode.MISSION_SERVICE_UNAVAILABLE;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }
}
