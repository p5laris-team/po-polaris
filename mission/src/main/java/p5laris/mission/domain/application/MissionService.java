package p5laris.mission.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.CompletionAnswer;
import com.p5laris.proto.mission.v1.CompletionInputType;
import com.p5laris.proto.mission.v1.CompletionQuestion;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetMissionDetailResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.MissionDetail;
import com.p5laris.proto.mission.v1.MissionCategory;
import com.p5laris.proto.mission.v1.MissionCharacterExp;
import com.p5laris.proto.mission.v1.MissionCharacterExpStatus;
import com.p5laris.proto.mission.v1.MissionDifficulty;
import com.p5laris.proto.mission.v1.MissionReward;
import com.p5laris.proto.mission.v1.MissionRewardStatus;
import com.p5laris.proto.mission.v1.MissionSatisfactionFeedback;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import com.p5laris.proto.mission.v1.TodayMission;
import com.p5laris.proto.mission.v1.UpsertMissionFeedbackResponse;
import com.p5laris.proto.mission.v1.WalletSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.application.diversity.MissionDiversitySnapshot;
import p5laris.mission.domain.application.event.MissionEventLogEvent;
import p5laris.mission.domain.application.guard.AiMissionCandidateGuardRequest;
import p5laris.mission.domain.application.guard.AiMissionCandidateGuardResult;
import p5laris.mission.domain.application.guard.AiMissionCandidateRejectionReason;
import p5laris.mission.domain.application.guard.MissionAiCandidateGuard;
import p5laris.mission.domain.application.guard.ValidatedAiMissionCandidate;
import p5laris.mission.domain.application.memory.MissionMemoryRecorder;
import p5laris.mission.domain.application.personalization.MissionPersonalizationContext;
import p5laris.mission.domain.application.personalization.MissionPersonalizationContextBuilder;
import p5laris.mission.domain.application.personalization.MissionRagContextService;
import p5laris.mission.domain.application.personalization.MissionRagQuery;
import p5laris.mission.domain.application.time.MissionTimeSlot;
import p5laris.mission.domain.application.weather.MissionWeatherContextService;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionFeedback;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.domain.enums.MissionFeedbackReaction;
import p5laris.mission.domain.domain.enums.MissionFeedbackReasonCode;
import p5laris.mission.domain.domain.enums.MissionFeedbackType;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionFeedbackRepository;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextClient;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextRequest;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;
import p5laris.mission.domain.infrastructure.grpc.CharacterExpGrantResult;
import p5laris.mission.domain.infrastructure.grpc.CharacterProfileClient;
import p5laris.mission.domain.infrastructure.grpc.MissionCharacterGrowth;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionService {

    /*
     * 아래 값들은 운영 토글이 아니라 현재 MVP 정책을 표현하는 도메인 상수다.
     * 배포 환경별로 달라져야 하는 값은 application.yaml/env로 분리하고,
     * 하루 보상/거절 횟수, 답변 길이, 멱등키 prefix처럼 API 정책과 함께 움직이는 값만 코드에 둔다.
     */
    private static final int MAX_DAILY_REWARD_COUNT = 20;
    private static final int MAX_DAILY_REJECT_COUNT = 10;
    private static final int COMPLETION_ANSWER_MIN_LENGTH = 1;
    private static final int COMPLETION_ANSWER_MAX_LENGTH = 300;
    private static final int ANSWER_PREVIEW_MAX_LENGTH = 40;
    private static final int FEEDBACK_REASON_TEXT_MAX_LENGTH = 100;
    private static final int AI_MISSION_CANDIDATE_MAX_ATTEMPTS = 2;
    private static final int EASY_REWARD_STAR_PIECE = 10;
    private static final int NORMAL_REWARD_STAR_PIECE = 15;
    private static final int CHALLENGE_REWARD_STAR_PIECE = 30;
    private static final String MISSION_REWARD_IDEMPOTENCY_KEY_PREFIX = "MISSION_REWARD:";
    private static final String MISSION_CHARACTER_EXP_IDEMPOTENCY_KEY_PREFIX = "MISSION_CHARACTER_EXP:";
    private static final String MISSION_TEXT_REQUEST_ID_PREFIX = "MISSION_TEXT:";
    private static final String MISSION_TEXT_RETRY_REQUEST_ID_SUFFIX = ":RETRY:";
    private static final String DEFAULT_COMPLETION_QUESTION = "해보고 나서 어땠어?";
    private static final String REJECTION_CHARACTER_MESSAGE = "괜찮아요. 다른 별을 찾아볼게요.";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Set<UserMissionStatus> ACTIVE_STATUSES = EnumSet.of(
            UserMissionStatus.OFFERED,
            UserMissionStatus.ANSWERING
    );

    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionTemplateSelector missionTemplateSelector;
    private final UserMissionRepository userMissionRepository;
    private final MissionCompletionAnswerRepository missionCompletionAnswerRepository;
    private final MissionFeedbackRepository missionFeedbackRepository;
    private final MissionOutboxEventRepository missionOutboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiMissionTextClient aiMissionTextClient;
    private final CharacterProfileClient characterProfileClient;
    private final MissionPersonalizationContextBuilder missionPersonalizationContextBuilder;
    private final MissionRagContextService missionRagContextService;
    private final MissionWeatherContextService missionWeatherContextService;
    private final MissionAiCandidateGuard missionAiCandidateGuard;
    private final MissionMemoryRecorder missionMemoryRecorder;
    private final WalletRewardClient walletRewardClient;
    private final MissionRewardDispatcher missionRewardDispatcher;
    private final MissionCharacterExpPolicy missionCharacterExpPolicy;
    private final MissionCharacterExpDispatcher missionCharacterExpDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;

    /**
     * 오늘 유저에게 진행 중인 현재 미션을 조회한다.
     *
     * 현재 미션의 기준은 characterId가 아니라 userId다.
     * characterId는 "어떤 캐릭터가 제안했는지"를 기록하기 위한 값이고,
     * MVP 정책상 한 유저는 하루에 OFFERED/ANSWERING 상태 미션을 하나만 가진다.
     */
    @Transactional(readOnly = true)
    public GetCurrentMissionResponse getCurrentMission(Long userId) {
        LocalDate today = LocalDate.now(clock);

        return userMissionRepository
                .findFirstByUserIdAndMissionDateAndStatusInOrderByStackOrderDesc(
                        userId,
                        today,
                        ACTIVE_STATUSES
                )
                .map(this::toProtoMission)
                .map(mission -> GetCurrentMissionResponse.newBuilder()
                        .setMission(mission)
                        .build())
                .orElseGet(() -> GetCurrentMissionResponse.newBuilder().build());
    }

    /**
     * 오늘 유저에게 제안된 미션 stack 전체를 조회한다.
     */
    @Transactional(readOnly = true)
    public GetTodayMissionsResponse getTodayMissions(Long userId) {
        return getTodayMissions(userId, LocalDate.now(clock));
    }

    /**
     * 특정 날짜의 미션 stack 전체를 조회한다.
     *
     * 이 메서드는 홈/미션 히스토리 화면용 읽기 API라 상태를 바꾸지 않는다.
     * 목록에는 답변 전문 대신 미리보기만 포함하고, 전문은 상세 조회에서만 내려준다.
     */
    @Transactional(readOnly = true)
    public GetTodayMissionsResponse getTodayMissions(Long userId, LocalDate missionDate) {
        LocalDate targetDate = missionDate == null ? LocalDate.now(clock) : missionDate;
        List<UserMission> missions = userMissionRepository.findByUserIdAndMissionDateOrderByStackOrderAsc(userId, targetDate);
        Map<Long, MissionCompletionAnswer> answerByMissionId = findAnswersByMissionId(missions);

        int completedCount = countByStatus(missions, UserMissionStatus.COMPLETED);
        int rejectedCount = countByStatus(missions, UserMissionStatus.REJECTED);
        int remainingRewardCount = remainingCount(MAX_DAILY_REWARD_COUNT, completedCount);
        int remainingRejectCount = remainingCount(MAX_DAILY_REJECT_COUNT, rejectedCount);

        GetTodayMissionsResponse.Builder builder = GetTodayMissionsResponse.newBuilder()
                .setMissionDate(targetDate.toString())
                // 기존 클라이언트 호환 필드다. 신규 화면은 maxDailyRewardCount를 우선 사용한다.
                .setMaxDailyOffers(MAX_DAILY_REWARD_COUNT)
                .setOfferedCount(missions.size())
                .setCompletedCount(completedCount)
                .setRejectedCount(rejectedCount)
                // 기존 클라이언트가 거절을 보상 횟수 차감으로 오해하지 않도록 남은 보상 횟수를 내려준다.
                .setRemainingOfferCount(remainingRewardCount)
                .setMaxDailyRewardCount(MAX_DAILY_REWARD_COUNT)
                .setCompletedRewardCount(completedCount)
                .setRemainingRewardCount(remainingRewardCount)
                .setMaxDailyRejectCount(MAX_DAILY_REJECT_COUNT)
                .setRemainingRejectCount(remainingRejectCount);

        findCurrentMissionId(missions).ifPresent(builder::setCurrentMissionId);
        missions.stream()
                .map(mission -> toProtoTodayMission(mission, answerByMissionId.get(mission.getId())))
                .forEach(builder::addMissions);

        return builder.build();
    }

    /**
     * 미션 상세 화면에서 완료 질문과 사용자가 제출한 답변 전문을 조회한다.
     */
    @Transactional(readOnly = true)
    public GetMissionDetailResponse getMissionDetail(Long userId, Long missionId) {
        UserMission mission = userMissionRepository.findByIdAndUserId(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
        Optional<MissionCompletionAnswer> answer = missionCompletionAnswerRepository.findByMissionId(mission.getId());
        Optional<MissionFeedback> satisfactionFeedback =
                missionFeedbackRepository.findByUserIdAndMissionIdAndFeedbackType(
                        userId,
                        mission.getId(),
                        MissionFeedbackType.SATISFACTION
                );

        return GetMissionDetailResponse.newBuilder()
                .setMission(toProtoMissionDetail(mission, answer.orElse(null), satisfactionFeedback.orElse(null)))
                .build();
    }

    /**
     * 다음 미션을 하나 생성해 OFFERED 상태로 저장한다.
     *
     * 진행 중인 미션이 이미 있으면 새 미션을 만들지 않는다.
     * 이 검사는 유저 기준으로 수행하고, characterId는 새 미션 row에 제안 캐릭터로 저장한다.
     *
     * 미션 row는 먼저 seed template fallback으로 저장하고, 그 뒤 트랜잭션 밖에서 ai 모듈을 호출한다.
     * AI 후보가 서버 검증을 통과하면 fallback row를 자율 미션으로 교체하고, 실패하면 seed fallback을 유지한다.
     */
    public CreateNextMissionResponse createNextMission(Long userId, Long characterId, Long lastMissionId) {
        LocalDate today = LocalDate.now(clock);
        validateMissionCreatable(userId, today);
        MissionPersonalizationContext personalizationContext = missionPersonalizationContextBuilder.build(userId, today);

        MissionCreationContext context = Objects.requireNonNull(transactionTemplate.execute(
                status -> createMissionWithFallback(userId, characterId, today)
        ));
        Optional<ValidatedAiMissionCandidate> generatedText = generateMissionText(context, personalizationContext);
        UserMission mission = Objects.requireNonNull(transactionTemplate.execute(
                status -> finalizeMissionCreation(userId, context.missionId(), generatedText)
        ));

        return CreateNextMissionResponse.newBuilder()
                .setMission(toProtoMission(mission))
                .build();
    }

    /**
     * 미션 생성 전 빠르게 막을 수 있는 조건을 확인한다.
     *
     * 이 검사는 외부 gRPC 호출 전에 실패를 반환하기 위한 1차 방어이고,
     * 실제 저장 트랜잭션 안에서 같은 검사를 한 번 더 수행한다.
     */
    private void validateMissionCreatable(Long userId, LocalDate today) {
        if (userMissionRepository.existsByUserIdAndMissionDateAndStatusIn(userId, today, ACTIVE_STATUSES)) {
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }

        validateDailyRewardLimit(userId, today);
    }

    private void validateDailyRewardLimit(Long userId, LocalDate missionDate) {
        long completedCount = userMissionRepository.countByUserIdAndMissionDateAndStatus(
                userId,
                missionDate,
                UserMissionStatus.COMPLETED
        );
        if (completedCount >= MAX_DAILY_REWARD_COUNT) {
            throw new MissionException(MissionErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        }
    }

    private void validateDailyRejectLimit(Long userId, LocalDate missionDate) {
        long rejectedCount = userMissionRepository.countByUserIdAndMissionDateAndStatus(
                userId,
                missionDate,
                UserMissionStatus.REJECTED
        );
        if (rejectedCount >= MAX_DAILY_REJECT_COUNT) {
            throw new MissionException(MissionErrorCode.MISSION_REJECT_LIMIT_EXCEEDED);
        }
    }

    /**
     * seed template 기준의 fallback 미션을 먼저 저장한다.
     *
     * 여기서 missionId가 생기므로, 이후 AI requestId를 안정적으로 만들 수 있다.
     * 완료 질문 row도 fallback 질문으로 먼저 만들어 두고, AI 성공 시 질문 텍스트만 교체한다.
     */
    private MissionCreationContext createMissionWithFallback(Long userId, Long characterId, LocalDate today) {
        validateMissionCreatable(userId, today);

        LocalDateTime now = LocalDateTime.now(clock);
        MissionTemplate template = selectNextTemplate(userId, today, MissionTimeSlot.from(now));
        int nextStackOrder = userMissionRepository.findMaxStackOrder(userId, today) + 1;
        UserMission userMission = UserMission.offerFromTemplate(
                userId,
                characterId,
                today,
                nextStackOrder,
                template,
                now
        );

        try {
            UserMission savedMission = userMissionRepository.saveAndFlush(userMission);
            missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                    savedMission.getId(),
                    savedMission.getUserId(),
                    template.getFallbackQuestion()
            ));
            return MissionCreationContext.from(savedMission, template, missionTextRequestId(savedMission));
        } catch (DataIntegrityViolationException e) {
            // 동시에 미션 생성 요청이 들어오면 애플리케이션의 exists 검사만으로는 중복 생성이 가능하다.
            // DB partial unique index가 마지막으로 막아주고, 여기서는 도메인 예외로 변환한다.
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }
    }

    /**
     * character/ai 모듈을 트랜잭션 밖에서 호출한다.
     *
     * character나 ai 모듈 호출이 실패하면 Optional.empty()를 반환해 fallback 미션으로 생성을 마무리한다.
     */
    private Optional<ValidatedAiMissionCandidate> generateMissionText(
            MissionCreationContext context,
            MissionPersonalizationContext personalizationContext
    ) {
        String recentMissionContextJson = missionRagContextService.enrich(
                context.userId(),
                new MissionRagQuery(
                        context.userId(),
                        context.missionTemplateId(),
                        context.baseTitle(),
                        context.baseDescription(),
                        context.category().name(),
                        context.difficulty().name()
                ),
                personalizationContext.recentMissionContextJson()
        );
        recentMissionContextJson = missionWeatherContextService.enrich(context.userId(), recentMissionContextJson);

        Optional<String> characterType = characterProfileClient.findActiveCharacterTypeCode(
                context.userId(),
                context.characterId()
        );
        if (characterType.isEmpty()) {
            return Optional.empty();
        }

        boolean challengeAlreadyUsedToday = userMissionRepository.existsByUserIdAndMissionDateAndDifficultyAndIdNot(
                context.userId(),
                context.missionDate(),
                MissionDifficultyType.CHALLENGE,
                context.missionId()
        );
        List<MissionDiversitySnapshot> todayMissionSnapshots = findTodayMissionDiversitySnapshots(context);

        for (int attempt = 0; attempt < AI_MISSION_CANDIDATE_MAX_ATTEMPTS; attempt++) {
            String requestId = aiMissionRequestId(context.aiRequestId(), attempt);
            Optional<AiMissionTextResult> generatedText = aiMissionTextClient.generateMissionTexts(new AiMissionTextRequest(
                        context.userId(),
                        context.characterId(),
                        characterType.get(),
                        context.missionTemplateId(),
                        context.baseTitle(),
                        context.baseDescription(),
                        context.category().name(),
                        context.difficulty().name(),
                        context.fallbackCharacterMessage(),
                        context.fallbackQuestion(),
                        context.fallbackCompletionResponse(),
                        personalizationContext.onboardingContextJson(),
                        recentMissionContextJson,
                        requestId
                ));

            if (generatedText.isEmpty()) {
                return Optional.empty();
            }

            AiMissionCandidateGuardResult guardResult = missionAiCandidateGuard.validate(new AiMissionCandidateGuardRequest(
                    context.userId(),
                    context.missionId(),
                    context.category(),
                    context.offeredAt(),
                    challengeAlreadyUsedToday,
                    todayMissionSnapshots,
                    generatedText.get()
            ));
            if (guardResult.accepted()) {
                return guardResult.candidate();
            }

            AiMissionCandidateRejectionReason reason = guardResult.rejectionReason()
                    .orElse(AiMissionCandidateRejectionReason.INVALID_CATEGORY_OR_DIFFICULTY);
            logRejectedAiCandidate(context, generatedText.get(), reason, attempt);
            if (!shouldRetryAiCandidate(reason, attempt)) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private List<MissionDiversitySnapshot> findTodayMissionDiversitySnapshots(MissionCreationContext context) {
        return userMissionRepository.findByUserIdAndMissionDateOrderByStackOrderAsc(
                        context.userId(),
                        context.missionDate()
                ).stream()
                // 지금 저장 중인 fallback row는 아직 사용자에게 보여준 미션이 아니므로 중복 비교 대상에서 제외한다.
                .filter(mission -> !Objects.equals(mission.getId(), context.missionId()))
                .map(MissionDiversitySnapshot::from)
                .toList();
    }

    private String aiMissionRequestId(String baseRequestId, int attempt) {
        if (attempt == 0) {
            return baseRequestId;
        }
        return baseRequestId + MISSION_TEXT_RETRY_REQUEST_ID_SUFFIX + attempt;
    }

    private boolean shouldRetryAiCandidate(AiMissionCandidateRejectionReason reason, int attempt) {
        return attempt + 1 < AI_MISSION_CANDIDATE_MAX_ATTEMPTS
                && reason != AiMissionCandidateRejectionReason.AI_FALLBACK_USED;
    }

    private void logRejectedAiCandidate(
            MissionCreationContext context,
            AiMissionTextResult generatedText,
            AiMissionCandidateRejectionReason reason,
            int attempt
    ) {
        log.info(
                "AI 자율 미션 후보를 seed fallback으로 대체합니다. reason={}, userId={}, missionId={}, aiGenerationId={}, requestId={}, attempt={}, candidateCategory={}, candidateDifficulty={}, fallbackCategory={}, fallbackDifficulty={}, fallbackUsed={}",
                reason,
                context.userId(),
                context.missionId(),
                generatedText.aiGenerationId(),
                generatedText.requestId(),
                attempt + 1,
                generatedText.category(),
                generatedText.difficulty(),
                context.category(),
                context.difficulty(),
                generatedText.fallbackUsed()
        );
    }

    /**
     * AI 결과가 서버 검증을 통과하면 자율 미션으로 교체하고, 없거나 검증에 실패하면 fallback 상태 그대로 확정한다.
     *
     * MISSION_OFFERED 이벤트는 최종 문구가 정해진 뒤 발행해 event-log metadata에 aiGenerationId를 담을 수 있게 한다.
     */
    private UserMission finalizeMissionCreation(
            Long userId,
            Long missionId,
            Optional<ValidatedAiMissionCandidate> generatedText
    ) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);
        MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseGet(() -> missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                        mission.getId(),
                        mission.getUserId(),
                        resolveCompletionQuestion(mission)
                )));

        generatedText.ifPresent(candidate -> {
            mission.applyGeneratedMission(
                    candidate.aiGenerationId(),
                    candidate.title(),
                    candidate.description(),
                    candidate.characterMessage(),
                    candidate.completionCharacterResponse(),
                    candidate.category(),
                    candidate.difficulty(),
                    rewardForDifficulty(candidate.difficulty())
            );
            answer.replaceQuestion(candidate.completionQuestion());
        });

        eventPublisher.publishEvent(MissionEventLogEvent.missionOffered(
                mission,
                toOccurredAt(mission.getOfferedAt())
        ));
        return mission;
    }

    private int rewardForDifficulty(MissionDifficultyType difficulty) {
        return switch (difficulty) {
            case EASY -> EASY_REWARD_STAR_PIECE;
            case NORMAL -> NORMAL_REWARD_STAR_PIECE;
            case CHALLENGE -> CHALLENGE_REWARD_STAR_PIECE;
        };
    }

    /**
     * 유저가 제안받았거나 완료 질문에 진입한 미션을 거절 처리한다.
     *
     * ANSWERING은 아직 답변 제출과 보상 지급이 끝나지 않은 상태라 사용자가 인증을 포기하고
     * 다른 미션을 받을 수 있게 REJECTED로 전환한다.
     *
     * 거절도 소유권 기준은 userId + missionId다.
     * characterId를 조건에 넣으면 같은 유저의 현재 미션을 다른 캐릭터 화면에서 처리할 때 실패할 수 있다.
     */
    @Transactional
    public RejectMissionResponse rejectMission(Long userId, Long missionId) {
        return rejectMission(userId, missionId, null, null);
    }

    @Transactional
    public RejectMissionResponse rejectMission(
            Long userId,
            Long missionId,
            String reasonCode,
            String reasonText
    ) {
        UserMission mission = userMissionRepository.findByIdAndUserId(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));

        if (!mission.isOffered() && !mission.isAnswering()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        validateDailyRejectLimit(userId, mission.getMissionDate());

        LocalDateTime now = LocalDateTime.now(clock);
        mission.reject(now);
        MissionFeedback feedback = upsertRejectionFeedback(mission, reasonCode, reasonText);
        missionMemoryRecorder.recordRejection(mission, feedback);
        eventPublisher.publishEvent(MissionEventLogEvent.missionRejected(mission, toOccurredAt(now)));

        return RejectMissionResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()))
                .setCharacterMessage(REJECTION_CHARACTER_MESSAGE)
                .build();
    }

    /**
     * 거절 이유 또는 완료 만족도 피드백을 저장한다.
     *
     * 피드백은 보상 조건이 아니라 이후 자율 미션 개인화에 쓰는 입력 신호다.
     */
    @Transactional
    public UpsertMissionFeedbackResponse upsertMissionFeedback(
            Long userId,
            Long missionId,
            MissionFeedbackType feedbackType,
            MissionFeedbackReaction reaction,
            String reasonCode,
            String reasonText
    ) {
        UserMission mission = userMissionRepository.findByIdAndUserId(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));

        if (feedbackType == MissionFeedbackType.SATISFACTION) {
            validateSatisfactionFeedback(mission, reaction);
            MissionFeedback feedback = upsertSatisfactionFeedback(mission, reaction);
            missionMemoryRecorder.recordSatisfaction(mission, feedback);
            return toFeedbackResponse(feedback);
        }

        if (feedbackType == MissionFeedbackType.REJECTION) {
            validateRejectionFeedback(mission);
            MissionFeedback feedback = upsertRejectionFeedback(mission, reasonCode, reasonText);
            missionMemoryRecorder.recordRejection(mission, feedback);
            return toFeedbackResponse(feedback);
        }

        throw new MissionException(MissionErrorCode.MISSION_FEEDBACK_INVALID);
    }

    /**
     * OFFERED 상태의 미션을 완료 질문 답변 중인 ANSWERING 상태로 전환한다.
     *
     * 모바일 중복 클릭이나 네트워크 재시도를 고려해 이미 ANSWERING이면 기존 질문을 다시 내려준다.
     */
    @Transactional
    public StartCompletionSessionResponse startCompletionSession(Long userId, Long missionId) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);

        if (mission.isCompleted()) {
            throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
        }

        if (!mission.isOffered() && !mission.isAnswering()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        boolean shouldPublishSessionStarted = mission.isOffered();
        LocalDateTime now = LocalDateTime.now(clock);
        if (shouldPublishSessionStarted) {
            mission.startAnswering(now);
        }

        MissionCompletionAnswer answer = findOrCreateCompletionAnswer(mission);
        if (shouldPublishSessionStarted) {
            eventPublisher.publishEvent(MissionEventLogEvent.completionSessionStarted(
                    mission,
                    answer,
                    toOccurredAt(now)
            ));
        }

        return StartCompletionSessionResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setQuestion(toProtoQuestion(answer))
                .build();
    }

    /**
     * 완료 질문에 대한 텍스트 답변을 저장하고 미션을 COMPLETED 상태로 전환한다.
     *
     * mission DB 쓰기와 wallet gRPC 호출을 한 트랜잭션에 넣지 않는다.
     * 답변 저장/상태 전환은 짧은 mission 트랜잭션으로 끝내고, 별조각 지급은 트랜잭션 밖에서 호출한다.
     * wallet 지급 요청은 mission_outbox_events에 기록하고, 성공하면 dispatcher가 보상 지급 완료 marker까지 저장한다.
     */
    public SubmitCompletionAnswerResponse submitCompletionAnswer(Long userId, Long missionId, String answerText) {
        String normalizedAnswer = validateAndNormalizeAnswer(answerText);
        MissionCompletionContext context = Objects.requireNonNull(transactionTemplate.execute(
                status -> completeMissionInTransaction(userId, missionId, normalizedAnswer)
        ));

        MissionCompletionReward reward = resolveReward(userId, context);
        MissionCompletionCharacterExp characterExp = resolveCharacterExp(userId, context);
        return toCompletionResponse(context.mission(), context.answer(), reward, characterExp);
    }

    /**
     * 완료 답변 저장과 미션 상태 전환만 담당하는 짧은 mission DB 트랜잭션이다.
     *
     * 같은 완료 API가 재시도되면 저장된 답변이 같은지 확인한 뒤 기존 완료 결과를 재사용한다.
     * 저장된 답변과 다른 답변으로 다시 제출하려는 경우에는 답변 수정을 허용하지 않는다.
     */
    private MissionCompletionContext completeMissionInTransaction(Long userId, Long missionId, String normalizedAnswer) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);
        String rewardIdempotencyKey = rewardIdempotencyKey(mission.getId());
        String characterExpIdempotencyKey = characterExpIdempotencyKey(mission.getId());
        int characterExpAmount = missionCharacterExpPolicy.calculateExp(mission.getDifficulty());

        if (mission.isCompleted()) {
            MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_INVALID_STATUS));
            if (!answer.hasSameAnswer(normalizedAnswer)) {
                throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
            }

            missionMemoryRecorder.recordCompletion(mission, answer);
            Optional<MissionOutboxEvent> characterExpOutbox =
                    missionOutboxEventRepository.findByIdempotencyKey(characterExpIdempotencyKey);

            if (mission.isRewardPaid()) {
                return new MissionCompletionContext(
                        mission,
                        answer,
                        true,
                        false,
                        rewardIdempotencyKey,
                        null,
                        null,
                        false,
                        characterExpIdempotencyKey,
                        characterExpAmount,
                        characterExpOutbox.map(MissionOutboxEvent::getId).orElse(null),
                        characterExpOutbox.map(MissionOutboxEvent::getStatus).orElse(null)
                );
            }

            MissionOutboxEvent outbox = ensureRewardOutbox(mission, rewardIdempotencyKey, LocalDateTime.now(clock));
            return new MissionCompletionContext(
                    mission,
                    answer,
                    false,
                    false,
                    rewardIdempotencyKey,
                    outbox.getId(),
                    outbox.getStatus(),
                    false,
                    characterExpIdempotencyKey,
                    characterExpAmount,
                    characterExpOutbox.map(MissionOutboxEvent::getId).orElse(null),
                    characterExpOutbox.map(MissionOutboxEvent::getStatus).orElse(null)
            );
        }

        if (!mission.isAnswering()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        validateDailyRewardLimit(userId, mission.getMissionDate());

        MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_INVALID_STATUS));

        if (answer.isAnswered()) {
            throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        answer.submit(normalizedAnswer, now);
        mission.complete(now);
        missionMemoryRecorder.recordCompletion(mission, answer);
        eventPublisher.publishEvent(MissionEventLogEvent.missionCompleted(mission, answer, toOccurredAt(now)));
        MissionOutboxEvent outbox = ensureRewardOutbox(mission, rewardIdempotencyKey, now);
        MissionOutboxEvent characterExpOutbox = ensureCharacterExpOutbox(
                mission,
                characterExpIdempotencyKey,
                characterExpAmount,
                now
        );

        return new MissionCompletionContext(
                mission,
                answer,
                false,
                true,
                rewardIdempotencyKey,
                outbox.getId(),
                outbox.getStatus(),
                true,
                characterExpIdempotencyKey,
                characterExpAmount,
                characterExpOutbox.getId(),
                characterExpOutbox.getStatus()
        );
    }

    /**
     * wallet 보상 지급 결과를 구한다.
     *
     * 이미 user_missions.idempotency_key가 있으면 보상 지급이 끝난 것으로 보고 새 거래를 만들지 않는다.
     * marker가 없으면 같은 MISSION_REWARD:{missionId} 키로 outbox를 즉시 발송해 중복 지급을 방어한다.
     */
    private MissionCompletionReward resolveReward(Long userId, MissionCompletionContext context) {
        if (context.rewardAlreadyPaid()) {
            return MissionCompletionReward.paid(walletRewardClient.getWalletStarPiece(userId));
        }

        if (!context.dispatchRewardNow()) {
            return MissionCompletionReward.withoutWallet(toProtoRewardStatus(context.rewardOutboxStatus()));
        }

        try {
            WalletRewardResult rewardResult = missionRewardDispatcher.dispatchNow(context.rewardOutboxId());
            return MissionCompletionReward.paid(rewardResult.starPiece());
        } catch (MissionException e) {
            if (e.getErrorCode() != MissionErrorCode.MISSION_REWARD_FAILED) {
                throw e;
            }

            log.warn(
                    "미션 완료는 성공했지만 즉시 보상 지급에 실패해 outbox 재처리로 전환합니다. missionId={}, userId={}, outboxId={}",
                    context.mission().getId(),
                    userId,
                    context.rewardOutboxId()
            );
            return MissionCompletionReward.withoutWallet(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        }
    }

    /**
     * 캐릭터 경험치 지급 결과를 구한다.
     *
     * 미션 완료 트랜잭션과 character gRPC 호출을 분리하고, 실패하면 PENDING으로 응답해 outbox 재처리에 맡긴다.
     */
    private MissionCompletionCharacterExp resolveCharacterExp(Long userId, MissionCompletionContext context) {
        if (context.characterExpOutboxId() == null) {
            return MissionCompletionCharacterExp.none();
        }

        if (!context.dispatchCharacterExpNow()) {
            return MissionCompletionCharacterExp.withoutGrowth(
                    context.characterExpAmount(),
                    toProtoCharacterExpStatus(context.characterExpOutboxStatus())
            );
        }

        try {
            CharacterExpGrantResult result = missionCharacterExpDispatcher.dispatchNow(context.characterExpOutboxId());
            return MissionCompletionCharacterExp.applied(context.characterExpAmount(), result);
        } catch (MissionException e) {
            if (e.getErrorCode() != MissionErrorCode.MISSION_CHARACTER_EXP_FAILED) {
                throw e;
            }

            log.warn(
                    "미션 완료는 성공했지만 즉시 캐릭터 경험치 지급에 실패해 outbox 재처리로 전환합니다. missionId={}, userId={}, outboxId={}",
                    context.mission().getId(),
                    userId,
                    context.characterExpOutboxId()
            );
            return MissionCompletionCharacterExp.withoutGrowth(
                    context.characterExpAmount(),
                    MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_PENDING
            );
        }
    }

    private MissionOutboxEvent ensureRewardOutbox(
            UserMission mission,
            String rewardIdempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        return missionOutboxEventRepository.findByIdempotencyKey(rewardIdempotencyKey)
                .orElseGet(() -> missionOutboxEventRepository.save(MissionOutboxEvent.rewardRequested(
                        mission,
                        objectMapper.valueToTree(new MissionRewardRequestedPayload(
                                mission.getId(),
                                mission.getUserId(),
                                mission.getRewardStarPiece()
                        )),
                        rewardIdempotencyKey,
                        nextAttemptAt
                )));
    }

    private MissionOutboxEvent ensureCharacterExpOutbox(
            UserMission mission,
            String characterExpIdempotencyKey,
            int expAmount,
            LocalDateTime nextAttemptAt
    ) {
        return missionOutboxEventRepository.findByIdempotencyKey(characterExpIdempotencyKey)
                .orElseGet(() -> missionOutboxEventRepository.save(MissionOutboxEvent.characterExpRequested(
                        mission,
                        objectMapper.valueToTree(new MissionCharacterExpRequestedPayload(
                                mission.getId(),
                                mission.getUserId(),
                                mission.getCharacterId(),
                                mission.getDifficulty().name(),
                                expAmount
                        )),
                        characterExpIdempotencyKey,
                        nextAttemptAt
                )));
    }

    /**
     * 오늘 아직 사용하지 않은 활성 미션 템플릿을 하나 고른다.
     *
     * AI 후보가 실패하거나 검증을 통과하지 못할 때 사용할 seed fallback template을 고른다.
     * 같은 날 같은 fallback 미션이 반복 제안되는 느낌을 줄이기 위해 오늘 사용한 template id는 제외한다.
     * 후보 선택 순서는 id 오름차순이 아니라 날짜 기준 랜덤 정책에 맡긴다.
     */
    private MissionTemplate selectNextTemplate(Long userId, LocalDate missionDate, MissionTimeSlot timeSlot) {
        List<Long> usedTemplateIds = userMissionRepository.findMissionTemplateIdsByUserIdAndMissionDate(userId, missionDate);
        Set<Long> usedTemplateIdSet = Set.copyOf(usedTemplateIds);
        List<MissionTemplate> activeTemplates = missionTemplateRepository.findByActiveTrueOrderByIdAsc();

        return missionTemplateSelector.selectNextTemplate(
                userId,
                missionDate,
                activeTemplates,
                usedTemplateIdSet,
                timeSlot
        );
    }

    private UserMission findOwnedMissionForUpdate(Long userId, Long missionId) {
        return userMissionRepository.findByIdAndUserIdForUpdate(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
    }

    private MissionCompletionAnswer findOrCreateCompletionAnswer(UserMission mission) {
        return missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseGet(() -> missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                        mission.getId(),
                        mission.getUserId(),
                        resolveCompletionQuestion(mission)
                )));
    }

    private String resolveCompletionQuestion(UserMission mission) {
        if (mission.getMissionTemplateId() == null) {
            return DEFAULT_COMPLETION_QUESTION;
        }

        return missionTemplateRepository.findById(mission.getMissionTemplateId())
                .map(MissionTemplate::getFallbackQuestion)
                .orElse(DEFAULT_COMPLETION_QUESTION);
    }

    private String validateAndNormalizeAnswer(String answerText) {
        if (answerText == null) {
            throw new MissionException(MissionErrorCode.MISSION_ANSWER_INVALID);
        }

        String normalizedAnswer = answerText.trim();
        if (normalizedAnswer.length() < COMPLETION_ANSWER_MIN_LENGTH
                || normalizedAnswer.length() > COMPLETION_ANSWER_MAX_LENGTH) {
            throw new MissionException(MissionErrorCode.MISSION_ANSWER_INVALID);
        }

        return normalizedAnswer;
    }

    private void validateSatisfactionFeedback(UserMission mission, MissionFeedbackReaction reaction) {
        if (!mission.isCompleted() || reaction == null) {
            throw new MissionException(MissionErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private void validateRejectionFeedback(UserMission mission) {
        if (mission.getStatus() != UserMissionStatus.REJECTED) {
            throw new MissionException(MissionErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private MissionFeedback upsertSatisfactionFeedback(
            UserMission mission,
            MissionFeedbackReaction reaction
    ) {
        MissionFeedback feedback = missionFeedbackRepository
                .findByUserIdAndMissionIdAndFeedbackType(
                        mission.getUserId(),
                        mission.getId(),
                        MissionFeedbackType.SATISFACTION
                )
                .orElseGet(() -> MissionFeedback.satisfaction(mission, reaction));
        feedback.updateSatisfaction(reaction);
        return missionFeedbackRepository.saveAndFlush(feedback);
    }

    private MissionFeedback upsertRejectionFeedback(
            UserMission mission,
            String reasonCode,
            String reasonText
    ) {
        MissionFeedbackReasonCode normalizedReasonCode = toReasonCode(reasonCode);
        String normalizedReasonText = normalizeReasonText(reasonText);
        MissionFeedback feedback = missionFeedbackRepository
                .findByUserIdAndMissionIdAndFeedbackType(
                        mission.getUserId(),
                        mission.getId(),
                        MissionFeedbackType.REJECTION
                )
                .orElseGet(() -> MissionFeedback.rejection(mission, normalizedReasonCode, normalizedReasonText));
        feedback.updateRejection(normalizedReasonCode, normalizedReasonText);
        return missionFeedbackRepository.saveAndFlush(feedback);
    }

    private MissionFeedbackReasonCode toReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return MissionFeedbackReasonCode.JUST_SKIP;
        }

        try {
            return MissionFeedbackReasonCode.valueOf(reasonCode.trim());
        } catch (IllegalArgumentException e) {
            throw new MissionException(MissionErrorCode.MISSION_FEEDBACK_INVALID);
        }
    }

    private String normalizeReasonText(String reasonText) {
        if (reasonText == null || reasonText.isBlank()) {
            return null;
        }

        String normalized = reasonText.trim();
        if (normalized.length() > FEEDBACK_REASON_TEXT_MAX_LENGTH) {
            throw new MissionException(MissionErrorCode.MISSION_FEEDBACK_INVALID);
        }
        return normalized;
    }

    private String rewardIdempotencyKey(Long missionId) {
        return MISSION_REWARD_IDEMPOTENCY_KEY_PREFIX + missionId;
    }

    private String characterExpIdempotencyKey(Long missionId) {
        return MISSION_CHARACTER_EXP_IDEMPOTENCY_KEY_PREFIX + missionId;
    }

    private String missionTextRequestId(UserMission mission) {
        return MISSION_TEXT_REQUEST_ID_PREFIX + sha256(String.join(
                "|",
                String.valueOf(mission.getUserId()),
                String.valueOf(mission.getId()),
                String.valueOf(mission.getMissionTemplateId())
        ));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }
    }

    private SubmitCompletionAnswerResponse toCompletionResponse(
            UserMission mission,
            MissionCompletionAnswer answer,
            MissionCompletionReward completionReward,
            MissionCompletionCharacterExp characterExp
    ) {
        SubmitCompletionAnswerResponse.Builder builder = SubmitCompletionAnswerResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setAnswer(toProtoAnswer(answer))
                .setReward(MissionReward.newBuilder()
                        .setStarPiece(mission.getRewardStarPiece())
                        .setAffection(0)
                        .build())
                .setCharacterMessage(mission.getCompletionCharacterResponse())
                .setRewardStatus(completionReward.rewardStatus());

        if (completionReward.walletStarPiece() != null) {
            builder.setWallet(WalletSnapshot.newBuilder()
                    .setStarPiece(completionReward.walletStarPiece())
                    .build());
        }

        if (characterExp.present()) {
            builder.setCharacterExp(toProtoCharacterExp(characterExp));
        }

        return builder.build();
    }

    private MissionRewardStatus toProtoRewardStatus(MissionOutboxEventStatus outboxStatus) {
        if (outboxStatus == MissionOutboxEventStatus.PROCESSING) {
            return MissionRewardStatus.MISSION_REWARD_STATUS_PROCESSING;
        }
        if (outboxStatus == MissionOutboxEventStatus.FAILED) {
            return MissionRewardStatus.MISSION_REWARD_STATUS_FAILED;
        }
        if (outboxStatus == MissionOutboxEventStatus.SUCCEEDED) {
            return MissionRewardStatus.MISSION_REWARD_STATUS_PAID;
        }
        return MissionRewardStatus.MISSION_REWARD_STATUS_PENDING;
    }

    private MissionCharacterExpStatus toProtoCharacterExpStatus(MissionOutboxEventStatus outboxStatus) {
        if (outboxStatus == MissionOutboxEventStatus.PROCESSING) {
            return MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_PROCESSING;
        }
        if (outboxStatus == MissionOutboxEventStatus.FAILED) {
            return MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_FAILED;
        }
        if (outboxStatus == MissionOutboxEventStatus.SUCCEEDED) {
            return MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_APPLIED;
        }
        return MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_PENDING;
    }

    private MissionCharacterExp toProtoCharacterExp(MissionCompletionCharacterExp characterExp) {
        MissionCharacterExp.Builder builder = MissionCharacterExp.newBuilder()
                .setExpAmount(characterExp.expAmount())
                .setExpGained(characterExp.expGained())
                .setLevelUp(characterExp.levelUp())
                .setStatus(characterExp.status());

        if (characterExp.beforeGrowth() != null) {
            builder.setBeforeGrowth(toProtoCharacterGrowth(characterExp.beforeGrowth()));
        }
        if (characterExp.afterGrowth() != null) {
            builder.setAfterGrowth(toProtoCharacterGrowth(characterExp.afterGrowth()));
        }

        return builder.build();
    }

    private com.p5laris.proto.character.v1.CharacterGrowth toProtoCharacterGrowth(MissionCharacterGrowth growth) {
        return com.p5laris.proto.character.v1.CharacterGrowth.newBuilder()
                .setLevel(growth.level())
                .setExp(growth.exp())
                .setCurrentLevelExp(growth.currentLevelExp())
                .setNextLevelExp(growth.nextLevelExp())
                .setExpToNextLevel(growth.expToNextLevel())
                .setProgressPercent(growth.progressPercent())
                .setGrowthStage(growth.growthStage())
                .setGrowthStageLabel(growth.growthStageLabel())
                .setMaxLevel(growth.maxLevel())
                .build();
    }

    private UpsertMissionFeedbackResponse toFeedbackResponse(MissionFeedback feedback) {
        UpsertMissionFeedbackResponse.Builder builder = UpsertMissionFeedbackResponse.newBuilder()
                .setMissionId(feedback.getMissionId())
                .setFeedbackType(toProtoFeedbackType(feedback.getFeedbackType()))
                .setUpdatedAt(formatDateTime(feedback.getUpdatedAt()));

        if (feedback.getReaction() != null) {
            builder.setReaction(toProtoFeedbackReaction(feedback.getReaction()));
        }
        if (feedback.getReasonCode() != null) {
            builder.setReasonCode(feedback.getReasonCode().name());
        }
        if (feedback.getReasonText() != null) {
            builder.setReasonText(feedback.getReasonText());
        }
        return builder.build();
    }

    /**
     * JPA Entity를 gRPC 응답용 Mission message로 변환한다.
     *
     * Entity를 외부 계약에 직접 노출하지 않고, proto 계약에 맞는 값만 골라 내려준다.
     */
    private com.p5laris.proto.mission.v1.Mission toProtoMission(UserMission mission) {
        return com.p5laris.proto.mission.v1.Mission.newBuilder()
                .setId(mission.getId())
                .setMissionDate(mission.getMissionDate().toString())
                .setStackOrder(mission.getStackOrder())
                .setTitle(mission.getTitle())
                .setDescription(mission.getDescription())
                .setCharacterMessage(mission.getCharacterMessage())
                .setCategory(toProtoCategory(mission))
                .setDifficulty(toProtoDifficulty(mission))
                .setRewardStarPiece(mission.getRewardStarPiece())
                .setStatus(toProtoStatus(mission.getStatus()))
                .build();
    }

    /**
     * 오늘 미션 히스토리 목록에 필요한 최소 필드만 proto로 변환한다.
     *
     * 완료 답변 전문은 민감할 수 있어 히스토리 응답에는 미리보기만 포함한다.
     */
    private TodayMission toProtoTodayMission(UserMission mission, MissionCompletionAnswer answer) {
        TodayMission.Builder builder = TodayMission.newBuilder()
                .setId(mission.getId())
                .setStackOrder(mission.getStackOrder())
                .setTitle(mission.getTitle())
                .setCategory(toProtoCategory(mission))
                .setDifficulty(toProtoDifficulty(mission))
                .setRewardStarPiece(mission.getRewardStarPiece())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setCharacterMessage(mission.getCharacterMessage())
                .setCreatedAt(formatDateTime(mission.getCreatedAt()))
                .setCompletedAt(formatDateTime(mission.getCompletedAt()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()));

        if (answer != null) {
            builder.setCompletionQuestion(answer.getQuestionText());
            if (answer.isAnswered()) {
                builder.setHasAnswer(true);
                builder.setAnswerPreview(toAnswerPreview(answer.getAnswerText()));
            }
        }

        return builder.build();
    }

    private MissionDetail toProtoMissionDetail(
            UserMission mission,
            MissionCompletionAnswer answer,
            MissionFeedback satisfactionFeedback
    ) {
        MissionDetail.Builder builder = MissionDetail.newBuilder()
                .setId(mission.getId())
                .setMissionDate(mission.getMissionDate().toString())
                .setStackOrder(mission.getStackOrder())
                .setTitle(mission.getTitle())
                .setDescription(mission.getDescription())
                .setCharacterMessage(mission.getCharacterMessage())
                .setCategory(toProtoCategory(mission))
                .setDifficulty(toProtoDifficulty(mission))
                .setRewardStarPiece(mission.getRewardStarPiece())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setCreatedAt(formatDateTime(mission.getCreatedAt()))
                .setCompletedAt(formatDateTime(mission.getCompletedAt()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()))
                .setCompletionCharacterResponse(nullToEmpty(mission.getCompletionCharacterResponse()));

        if (answer != null) {
            builder.setQuestion(toProtoQuestion(answer));
            if (answer.isAnswered()) {
                builder.setAnswer(toProtoAnswer(answer));
                builder.setHasAnswer(true);
            }
        }

        if (satisfactionFeedback != null && satisfactionFeedback.getReaction() != null) {
            builder.setSatisfactionFeedback(MissionSatisfactionFeedback.newBuilder()
                    .setReaction(toProtoFeedbackReaction(satisfactionFeedback.getReaction()))
                    .setUpdatedAt(formatDateTime(satisfactionFeedback.getUpdatedAt()))
                    .build());
        }

        return builder.build();
    }

    private int countByStatus(List<UserMission> missions, UserMissionStatus status) {
        return (int) missions.stream()
                .filter(mission -> mission.getStatus() == status)
                .count();
    }

    private Map<Long, MissionCompletionAnswer> findAnswersByMissionId(List<UserMission> missions) {
        if (missions.isEmpty()) {
            return Map.of();
        }

        List<Long> missionIds = missions.stream()
                .map(UserMission::getId)
                .toList();
        return missionCompletionAnswerRepository.findByMissionIdIn(missionIds).stream()
                .collect(Collectors.toMap(MissionCompletionAnswer::getMissionId, Function.identity()));
    }

    private int remainingCount(int maxCount, int usedCount) {
        return Math.max(0, maxCount - usedCount);
    }

    private String toAnswerPreview(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return "";
        }

        String normalized = answerText.strip();
        if (normalized.length() <= ANSWER_PREVIEW_MAX_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, ANSWER_PREVIEW_MAX_LENGTH) + "...";
    }

    private Optional<Long> findCurrentMissionId(List<UserMission> missions) {
        return missions.stream()
                .filter(mission -> ACTIVE_STATUSES.contains(mission.getStatus()))
                .map(UserMission::getId)
                .findFirst();
    }

    private CompletionQuestion toProtoQuestion(MissionCompletionAnswer answer) {
        return CompletionQuestion.newBuilder()
                .setId(answer.getId())
                .setText(answer.getQuestionText())
                .setInputType(CompletionInputType.COMPLETION_INPUT_TYPE_TEXT)
                .setMinLength(COMPLETION_ANSWER_MIN_LENGTH)
                .setMaxLength(COMPLETION_ANSWER_MAX_LENGTH)
                .build();
    }

    private CompletionAnswer toProtoAnswer(MissionCompletionAnswer answer) {
        return CompletionAnswer.newBuilder()
                .setText(answer.getAnswerText())
                .setAnsweredAt(formatDateTime(answer.getAnsweredAt()))
                .build();
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionCategory proto enum으로 변환한다.
    private MissionCategory toProtoCategory(UserMission mission) {
        return MissionCategory.valueOf("MISSION_CATEGORY_" + mission.getCategory().name());
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionDifficulty proto enum으로 변환한다.
    private MissionDifficulty toProtoDifficulty(UserMission mission) {
        return MissionDifficulty.valueOf("MISSION_DIFFICULTY_" + mission.getDifficulty().name());
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionStatus proto enum으로 변환한다.
    private MissionStatus toProtoStatus(UserMissionStatus status) {
        return MissionStatus.valueOf("MISSION_STATUS_" + status.name());
    }

    private com.p5laris.proto.mission.v1.MissionFeedbackType toProtoFeedbackType(MissionFeedbackType feedbackType) {
        return com.p5laris.proto.mission.v1.MissionFeedbackType.valueOf(
                "MISSION_FEEDBACK_TYPE_" + feedbackType.name()
        );
    }

    private com.p5laris.proto.mission.v1.MissionFeedbackReaction toProtoFeedbackReaction(
            MissionFeedbackReaction reaction
    ) {
        return com.p5laris.proto.mission.v1.MissionFeedbackReaction.valueOf(
                "MISSION_FEEDBACK_REACTION_" + reaction.name()
        );
    }

    // proto에는 LocalDateTime 타입을 직접 쓰지 않으므로 ISO 문자열로 변환한다.
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private OffsetDateTime toOccurredAt(LocalDateTime dateTime) {
        return dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }

    /**
     * 완료 처리 트랜잭션이 끝난 뒤 wallet 호출에 필요한 미션/답변 스냅샷이다.
     *
     * rewardAlreadyPaid가 true면 이미 mission row에 보상 지급 marker가 있으므로 wallet 적립을 다시 호출하지 않는다.
     * rewardOutboxId는 marker가 없을 때 dispatcher가 즉시 발송할 outbox row id다.
     */
    private record MissionCompletionContext(
            UserMission mission,
            MissionCompletionAnswer answer,
            boolean rewardAlreadyPaid,
            boolean dispatchRewardNow,
            String rewardIdempotencyKey,
            Long rewardOutboxId,
            MissionOutboxEventStatus rewardOutboxStatus,
            boolean dispatchCharacterExpNow,
            String characterExpIdempotencyKey,
            int characterExpAmount,
            Long characterExpOutboxId,
            MissionOutboxEventStatus characterExpOutboxStatus
    ) {
    }

    private record MissionCompletionReward(
            MissionRewardStatus rewardStatus,
            Integer walletStarPiece
    ) {

        private static MissionCompletionReward paid(int walletStarPiece) {
            return new MissionCompletionReward(MissionRewardStatus.MISSION_REWARD_STATUS_PAID, walletStarPiece);
        }

        private static MissionCompletionReward withoutWallet(MissionRewardStatus rewardStatus) {
            return new MissionCompletionReward(rewardStatus, null);
        }
    }

    private record MissionCompletionCharacterExp(
            boolean present,
            int expAmount,
            int expGained,
            MissionCharacterExpStatus status,
            MissionCharacterGrowth beforeGrowth,
            MissionCharacterGrowth afterGrowth,
            boolean levelUp
    ) {

        private static MissionCompletionCharacterExp none() {
            return new MissionCompletionCharacterExp(
                    false,
                    0,
                    0,
                    MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_UNSPECIFIED,
                    null,
                    null,
                    false
            );
        }

        private static MissionCompletionCharacterExp applied(int expAmount, CharacterExpGrantResult result) {
            return new MissionCompletionCharacterExp(
                    true,
                    expAmount,
                    result.expGained(),
                    MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_APPLIED,
                    result.beforeGrowth(),
                    result.afterGrowth(),
                    result.levelUp()
            );
        }

        private static MissionCompletionCharacterExp withoutGrowth(
                int expAmount,
                MissionCharacterExpStatus status
        ) {
            return new MissionCompletionCharacterExp(
                    true,
                    expAmount,
                    status == MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_APPLIED ? expAmount : 0,
                    status,
                    null,
                    null,
                    false
            );
        }
    }

    /**
     * fallback 미션 저장 후 AI 자율 미션 후보 생성을 요청하는 데 필요한 값만 담은 스냅샷이다.
     *
     * 이 record는 트랜잭션 밖에서 사용되므로 JPA Entity 대신 문자열/ID 값만 들고 간다.
     */
    private record MissionCreationContext(
            Long missionId,
            Long userId,
            Long characterId,
            Long missionTemplateId,
            LocalDate missionDate,
            LocalDateTime offeredAt,
            String baseTitle,
            String baseDescription,
            MissionCategoryType category,
            MissionDifficultyType difficulty,
            String fallbackCharacterMessage,
            String fallbackQuestion,
            String fallbackCompletionResponse,
            String aiRequestId
    ) {

        private static MissionCreationContext from(
                UserMission mission,
                MissionTemplate template,
                String aiRequestId
        ) {
            return new MissionCreationContext(
                    mission.getId(),
                    mission.getUserId(),
                    mission.getCharacterId(),
                    template.getId(),
                    mission.getMissionDate(),
                    mission.getOfferedAt(),
                    template.getBaseTitle(),
                    template.getBaseDescription(),
                    template.getCategory(),
                    template.getDifficulty(),
                    template.getFallbackCharacterMessage(),
                    template.getFallbackQuestion(),
                    template.getFallbackCompletionResponse(),
                    aiRequestId
            );
        }
    }
}
