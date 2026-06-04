package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.CompletionInputType;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.MissionCharacterExpStatus;
import com.p5laris.proto.mission.v1.MissionRewardStatus;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import com.p5laris.proto.mission.v1.UpsertMissionFeedbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionFeedback;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionFeedbackReaction;
import p5laris.mission.domain.domain.enums.MissionFeedbackReasonCode;
import p5laris.mission.domain.domain.enums.MissionFeedbackType;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionFeedbackRepository;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMemoryRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextClient;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextRequest;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingClient;
import p5laris.mission.domain.infrastructure.grpc.CharacterExpClient;
import p5laris.mission.domain.infrastructure.grpc.CharacterExpGrantResult;
import p5laris.mission.domain.infrastructure.grpc.CharacterProfileClient;
import p5laris.mission.domain.infrastructure.grpc.MissionCharacterGrowth;
import p5laris.mission.domain.infrastructure.grpc.NotificationPushClient;
import p5laris.mission.domain.infrastructure.grpc.OnboardingProfileClient;
import p5laris.mission.domain.infrastructure.grpc.OnboardingProfileClient.OnboardingProfileSnapshot;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "grpc.client.notification.address=static://localhost:9098",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.data.redis.timeout=200ms",
        "mission.weather.enabled=false",
        "mission.weather.provider=kma",
        "mission.weather.default-nx=60",
        "mission.weather.default-ny=127",
        "mission.weather.default-location-label=SEOUL",
        "mission.weather.timeout-ms=1500",
        "mission.weather.cache-ttl-seconds=1800",
        "mission.weather.redis-cache-enabled=false",
        "mission.weather.rate-limit-enabled=false",
        "mission.weather.rate-limit-requests-per-minute=60",
        "mission.weather.rate-limit-key-ttl-seconds=70",
        "mission.weather.rate-limit-fail-closed=true",
        "mission.weather.kma.base-url=http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0",
        "mission.weather.kma.service-key=test-key",
        "mission.rag.enabled=false",
        "mission.rag.embedding-model=gemini-embedding-001",
        "mission.rag.embedding-dimension=768",
        "mission.rag.top-k=5",
        "mission.rag.similarity-threshold=0.72",
        "mission.rag.fallback-to-recent-memory=true",
        "mission.memory-embedding.enabled=false",
        "mission.memory-embedding.fixed-delay-ms=60000",
        "mission.memory-embedding.initial-delay-ms=60000",
        "mission.memory-embedding.batch-size=20",
        "mission.memory-embedding.max-attempts=3",
        "mission.memory-embedding.processing-timeout-seconds=60",
        "mission.memory-embedding.retry-initial-delay-seconds=60",
        "mission.memory-embedding.retry-max-delay-seconds=1800",
        "mission.reward-outbox.enabled=false",
        "mission.reward-outbox.fixed-delay-ms=60000",
        "mission.reward-outbox.initial-delay-ms=60000",
        "mission.reward-outbox.batch-size=10",
        "mission.reward-outbox.max-attempts=5",
        "mission.reward-outbox.processing-timeout-seconds=60",
        "mission.reward-outbox.retry-initial-delay-seconds=60",
        "mission.reward-outbox.retry-max-delay-seconds=3600",
        "mission.reward-wallet.deadline-ms=1000",
        "mission.character-exp.deadline-ms=1000",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class MissionServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long CHARACTER_ID = 2001L;
    private static final Long OTHER_CHARACTER_ID = 2002L;

    @Autowired
    private MissionService missionService;

    @Autowired
    private Clock clock;

    @Autowired
    private MissionRewardDispatcher missionRewardDispatcher;

    @Autowired
    private MissionCharacterExpDispatcher missionCharacterExpDispatcher;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private UserMissionRepository userMissionRepository;

    @Autowired
    private MissionCompletionAnswerRepository missionCompletionAnswerRepository;

    @Autowired
    private MissionFeedbackRepository missionFeedbackRepository;

    @Autowired
    private UserMemoryRepository userMemoryRepository;

    @Autowired
    private MissionOutboxEventRepository missionOutboxEventRepository;

    @Autowired
    private MissionTemplateRepository missionTemplateRepository;

    @MockitoBean
    private WalletRewardClient walletRewardClient;

    @MockitoBean
    private AiMissionTextClient aiMissionTextClient;

    @MockitoBean
    private AiTextEmbeddingClient aiTextEmbeddingClient;

    @MockitoBean
    private CharacterExpClient characterExpClient;

    @MockitoBean
    private CharacterProfileClient characterProfileClient;

    @MockitoBean
    private OnboardingProfileClient onboardingProfileClient;

    @MockitoBean
    private NotificationPushClient notificationPushClient;

    @BeforeEach
    void setUp() {
        userMemoryRepository.deleteAll();
        missionOutboxEventRepository.deleteAll();
        missionFeedbackRepository.deleteAll();
        missionCompletionAnswerRepository.deleteAll();
        userMissionRepository.deleteAll();
        reset(walletRewardClient, aiMissionTextClient, aiTextEmbeddingClient, characterExpClient, characterProfileClient, onboardingProfileClient, notificationPushClient);
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new WalletRewardResult(110, 9001L));
        when(walletRewardClient.getWalletStarPiece(anyLong()))
                .thenReturn(110);
        when(characterExpClient.grantMissionCompletionExp(anyLong(), anyLong(), anyLong(), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    Long characterId = invocation.getArgument(1);
                    int expAmount = invocation.getArgument(3);
                    return new CharacterExpGrantResult(
                            characterId,
                            expAmount,
                            characterGrowth(0),
                            characterGrowth(expAmount),
                            false,
                            false
                    );
                });
        when(characterProfileClient.findActiveCharacterTypeCode(anyLong(), anyLong()))
                .thenReturn(Optional.of("NOVA"));
        when(onboardingProfileClient.findProfile(anyLong()))
                .thenReturn(Optional.empty());
        ReflectionTestUtils.setField(missionService, "clock", clock);
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    String category = request == null ? "BASIC_ROUTINE" : request.category();
                    return Optional.of(aiMissionTextResult(
                            501L,
                            "AI가 만든 자율 미션",
                            "AI가 만든 자율 미션 설명",
                            category,
                            "EASY"
                    ));
                });
    }

    @Test
    void 현재_미션이_없으면_빈_응답을_반환한다() {
        GetCurrentMissionResponse response = missionService.getCurrentMission(USER_ID);

        assertThat(response.hasMission()).isFalse();
    }

    @Test
    void 다음_미션을_생성하면_OFFERED_상태의_현재_미션이_된다() {
        assertThat(missionTemplateRepository.count()).isPositive();

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        GetCurrentMissionResponse current = missionService.getCurrentMission(USER_ID);

        assertThat(created.hasMission()).isTrue();
        assertThat(created.getMission().getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_OFFERED);
        assertThat(created.getMission().getRewardStarPiece()).isEqualTo(10);
        assertThat(created.getMission().getTitle()).isEqualTo("AI가 만든 자율 미션");
        assertThat(created.getMission().getDescription()).isEqualTo("AI가 만든 자율 미션 설명");
        assertThat(created.getMission().getCharacterMessage()).isEqualTo("AI가 바꾼 제안 문구");
        assertThat(current.hasMission()).isTrue();
        assertThat(current.getMission().getId()).isEqualTo(created.getMission().getId());

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        assertThat(savedMission.getAiGenerationId()).isEqualTo(501L);
        assertThat(savedMission.getMissionTemplateId()).isNull();
        assertThat(savedMission.getCompletionCharacterResponse()).isEqualTo("AI가 만든 완료 반응");
        assertThat(savedAnswer.getQuestionText()).isEqualTo("AI가 만든 완료 질문");
    }

    @Test
    void AI_후보_생성에_실패하면_template_fallback_미션을_생성한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.empty());

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getCharacterMessage()).isEqualTo(template.getFallbackCharacterMessage());
        assertThat(savedMission.getCompletionCharacterResponse()).isEqualTo(template.getFallbackCompletionResponse());
        assertThat(savedAnswer.getQuestionText()).isEqualTo(template.getFallbackQuestion());
    }

    @Test
    void AI가_fallback_결과를_반환하면_seed_template_fallback을_유지한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.of(aiMissionTextResult(
                        601L,
                        "fallback 후보 제목",
                        "fallback 후보 설명",
                        "BASIC_ROUTINE",
                        "EASY",
                        true
                )));

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo(template.getBaseTitle());
        assertThat(savedMission.getCharacterMessage()).isEqualTo(template.getFallbackCharacterMessage());
        assertThat(savedAnswer.getQuestionText()).isEqualTo(template.getFallbackQuestion());
    }

    @Test
    void AI_후보가_정책을_위반하면_한_번_재요청하고_통과한_후보를_저장한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            901L,
                            "첫 번째 후보",
                            "첫 번째 후보 설명",
                            differentCategory(request.category()),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            902L,
                            "다시 고른 후보",
                            "다시 고른 후보 설명",
                            request.category(),
                            "EASY"
                    ));
                });
        ArgumentCaptor<AiMissionTextRequest> requestCaptor = ArgumentCaptor.forClass(AiMissionTextRequest.class);

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isEqualTo(902L);
        assertThat(savedMission.getMissionTemplateId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo("다시 고른 후보");
        verify(aiMissionTextClient, times(2)).generateMissionTexts(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).requestId())
                .startsWith(requestCaptor.getAllValues().get(0).requestId())
                .endsWith(":RETRY:1");
    }

    @Test
    void AI_후보가_두_번_모두_정책을_위반하면_seed_template_fallback을_유지한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            911L,
                            "첫 번째 후보",
                            "첫 번째 후보 설명",
                            differentCategory(request.category()),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            912L,
                            "두 번째 후보",
                            "두 번째 후보 설명",
                            differentCategory(request.category()),
                            "EASY"
                    ));
                });

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo(template.getBaseTitle());
        assertThat(savedMission.getDescription()).isEqualTo(template.getBaseDescription());
        verify(aiMissionTextClient, times(2)).generateMissionTexts(any());
    }

    @Test
    void AI_후보가_오늘_이미_본_행동군과_겹치면_한_번_재요청한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            921L,
                            "물 한 모금 마시기",
                            "가까운 물을 한 모금 마시고 잠깐 숨을 골라보세요.",
                            request.category(),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            922L,
                            "수분 채우기",
                            "물을 한 잔 마시며 몸에 필요한 수분을 채워보세요.",
                            request.category(),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            923L,
                            "집중 타이머 켜기",
                            "5분 동안 할 일을 하나 정하고 타이머를 켜보세요.",
                            request.category(),
                            "EASY"
                    ));
                });

        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.rejectMission(USER_ID, first.getMission().getId());

        CreateNextMissionResponse second = missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());
        UserMission savedSecond = userMissionRepository.findById(second.getMission().getId()).orElseThrow();

        assertThat(savedSecond.getAiGenerationId()).isEqualTo(923L);
        assertThat(savedSecond.getMissionTemplateId()).isNull();
        assertThat(savedSecond.getTitle()).isEqualTo("집중 타이머 켜기");
        verify(aiMissionTextClient, times(3)).generateMissionTexts(any());
    }

    @Test
    void AI_후보가_오늘_이미_본_행동군과_두_번_겹치면_seed_template_fallback을_유지한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            931L,
                            "물 한 모금 마시기",
                            "가까운 물을 한 모금 마시고 잠깐 숨을 골라보세요.",
                            request.category(),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            932L,
                            "수분 채우기",
                            "물을 한 잔 마시며 몸에 필요한 수분을 채워보세요.",
                            request.category(),
                            "EASY"
                    ));
                })
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            933L,
                            "따뜻한 차 한 모금",
                            "가까운 컵에 담긴 음료를 한 모금 마셔보세요.",
                            request.category(),
                            "EASY"
                    ));
                });

        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.rejectMission(USER_ID, first.getMission().getId());

        CreateNextMissionResponse second = missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());
        UserMission savedSecond = userMissionRepository.findById(second.getMission().getId()).orElseThrow();
        var template = missionTemplateRepository.findById(savedSecond.getMissionTemplateId()).orElseThrow();

        assertThat(savedSecond.getAiGenerationId()).isNull();
        assertThat(savedSecond.getTitle()).isEqualTo(template.getBaseTitle());
        assertThat(savedSecond.getDescription()).isEqualTo(template.getBaseDescription());
        verify(aiMissionTextClient, times(3)).generateMissionTexts(any());
    }

    @Test
    void AI_제목이나_설명에_캐릭터_해석_형식이_섞이면_seed_template_fallback을_유지한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.of(aiMissionTextResult(
                        602L,
                        "무우... 무...? (해석: 몸 좌우 균형 느끼기)",
                        "서 있거나 앉은 상태에서 몸이 어느 쪽으로 기우는지 느껴보세요.",
                        "BODY_CARE",
                        "EASY"
                )));

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo(template.getBaseTitle());
        assertThat(savedMission.getDescription()).isEqualTo(template.getBaseDescription());
        assertThat(savedAnswer.getQuestionText()).isEqualTo(template.getFallbackQuestion());
    }

    @Test
    void AI_제목이_캐릭터_발화로_시작하면_seed_template_fallback을_유지한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.of(aiMissionTextResult(
                        603L,
                        "무우... 몸 좌우 균형 느끼기",
                        "서 있거나 앉은 상태에서 몸이 어느 쪽으로 기우는지 느껴보세요.",
                        "BODY_CARE",
                        "EASY"
                )));

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo(template.getBaseTitle());
    }

    @Test
    void AI가_NORMAL_난이도를_반환하면_보상을_15개로_확정한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            601L,
                            "3분 창가 숨 고르기",
                            "창가나 가까운 자리에서 3분 동안 천천히 숨을 골라보세요.",
                            request.category(),
                            "NORMAL"
                    ));
                });

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(created.getMission().getRewardStarPiece()).isEqualTo(15);
        assertThat(created.getMission().getDifficulty().name()).isEqualTo("MISSION_DIFFICULTY_NORMAL");
        assertThat(savedMission.getAiGenerationId()).isEqualTo(601L);
        assertThat(savedMission.getMissionTemplateId()).isNull();
    }

    @Test
    void AI가_CHALLENGE_난이도를_반환하면_보상을_30개로_확정한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    return Optional.of(aiMissionTextResult(
                            701L,
                            "책상 한 구역 깊게 정리",
                            "책상 위 한 구역을 정해서 5분 동안 물건을 분류하고 닦아보세요.",
                            request.category(),
                            "CHALLENGE"
                    ));
                });

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(created.getMission().getRewardStarPiece()).isEqualTo(30);
        assertThat(created.getMission().getDifficulty().name()).isEqualTo("MISSION_DIFFICULTY_CHALLENGE");
        assertThat(savedMission.getAiGenerationId()).isEqualTo(701L);
        assertThat(savedMission.getMissionTemplateId()).isNull();
    }

    @Test
    void CHALLENGE_미션은_하루에_한_번만_AI_후보로_확정한다() {
        AtomicInteger aiGenerationSequence = new AtomicInteger(700);
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    long aiGenerationId = aiGenerationSequence.incrementAndGet();
                    return Optional.of(aiMissionTextResult(
                            aiGenerationId,
                            aiGenerationId == 701L ? "첫 도전 미션" : "두 번째 도전 미션",
                            aiGenerationId == 701L ? "5분짜리 첫 도전 미션이에요." : "5분짜리 두 번째 도전 미션이에요.",
                            request.category(),
                            "CHALLENGE"
                    ));
                });
        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.rejectMission(USER_ID, first.getMission().getId());

        CreateNextMissionResponse second = missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());
        UserMission savedSecond = userMissionRepository.findById(second.getMission().getId()).orElseThrow();

        assertThat(savedSecond.getAiGenerationId()).isNull();
        assertThat(savedSecond.getMissionTemplateId()).isNotNull();
        assertThat(second.getMission().getRewardStarPiece()).isNotEqualTo(30);
    }

    @Test
    void 온보딩과_최근_미션_context를_AI_생성_요청에_전달한다() {
        when(onboardingProfileClient.findProfile(USER_ID))
                .thenReturn(Optional.of(new OnboardingProfileSnapshot(
                        2,
                        List.of("MOVE_BODY", "FOCUS"),
                        List.of("MORNING"),
                        List.of("HOME"),
                        "NORMAL",
                        List.of("OUTDOOR"),
                        true
                )));
        ArgumentCaptor<AiMissionTextRequest> requestCaptor = ArgumentCaptor.forClass(AiMissionTextRequest.class);

        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        verify(aiMissionTextClient).generateMissionTexts(requestCaptor.capture());
        AiMissionTextRequest request = requestCaptor.getValue();
        assertThat(request.onboardingContextJson())
                .contains("\"available\":true")
                .contains("\"routineGoals\":[\"MOVE_BODY\",\"FOCUS\"]")
                .contains("\"missionIntensity\":\"NORMAL\"");
        assertThat(request.recentMissionContextJson())
                .contains("\"policyContext\"")
                .contains("\"allowedDifficulties\"")
                .contains("\"environmentContext\"")
                .contains("\"currentTimeSlot\"")
                .contains("\"timeSlotPolicy\"")
                .contains("\"locationPolicy\"")
                .contains("\"weatherPolicy\"")
                .contains("\"weather\":null")
                .contains("\"source\":\"NONE\"")
                .contains("\"blockedCategories\"")
                .contains("\"blockedMissionKeywords\"")
                .contains("\"memoryPolicy\"")
                .contains("\"referenceOnly\":true")
                .contains("\"diversityPolicy\"")
                .contains("\"avoidSameActionFamily\":true")
                .contains("\"avoidSameCoreObject\":true")
                .contains("\"serverGuard\"")
                .contains("\"todayMissionDiversity\"")
                .contains("\"userMemories\":[]")
                .contains("\"recentMissions\"");
    }

    @Test
    void 사용자_기억_context를_AI_생성_요청에_포함한다() {
        ArgumentCaptor<AiMissionTextRequest> requestCaptor = ArgumentCaptor.forClass(AiMissionTextRequest.class);
        AtomicInteger aiGenerationSequence = new AtomicInteger(500);
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenAnswer(invocation -> {
                    AiMissionTextRequest request = invocation.getArgument(0);
                    int sequence = aiGenerationSequence.incrementAndGet();
                    return Optional.of(aiMissionTextResult(
                            (long) sequence,
                            "AI가 만든 자율 미션 " + sequence,
                            "AI가 만든 자율 미션 설명 " + sequence,
                            request.category(),
                            "EASY"
                    ));
                });
        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, first.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, first.getMission().getId(), "어깨가 덜 뻐근했어");

        missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());

        verify(aiMissionTextClient, times(2)).generateMissionTexts(requestCaptor.capture());
        String secondContext = requestCaptor.getAllValues().get(1).recentMissionContextJson();
        assertThat(secondContext)
                .contains("\"memoryPolicy\"")
                .contains("\"available\":true")
                .contains("\"referenceOnly\":true")
                .contains("\"todayMissionDiversity\"")
                .contains("\"actionFamily\"")
                .contains("\"userMemories\"")
                .contains("\"type\":\"MISSION_COMPLETION\"")
                .contains("\"sourceType\":\"MISSION_COMPLETION_ANSWER\"")
                .contains("어깨가 덜 뻐근했어")
                .contains("\"importance\":70")
                .contains("\"metadata\"");
    }

    @Test
    void 밤에_AI가_햇빛_미션을_반환하면_seed_template_fallback을_유지한다() {
        ReflectionTestUtils.setField(missionService, "clock", fixedClockAt(2026, 5, 25, 22, 30));
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.of(aiMissionTextResult(
                        801L,
                        "짧은 햇빛 충전하기",
                        "가능하다면 햇빛이나 밝은 곳에 1분 정도 머물러보세요.",
                        "OUTDOOR_LIGHT",
                        "EASY"
                )));

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getTitle()).isEqualTo(template.getBaseTitle());
        assertThat(savedMission.getCategory().name()).isNotEqualTo("OUTDOOR_LIGHT");
    }

    @Test
    void 진행_중인_미션이_있으면_다음_미션을_추가로_생성할_수_없다() {
        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        verify(aiMissionTextClient, times(1)).generateMissionTexts(any());
    }

    @Test
    void 진행_중인_미션은_캐릭터가_달라도_유저_기준으로_하나만_허용한다() {
        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, OTHER_CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
    }

    @Test
    void OFFERED_미션은_거절할_수_있다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        RejectMissionResponse rejected = missionService.rejectMission(
                USER_ID,
                created.getMission().getId()
        );
        GetCurrentMissionResponse current = missionService.getCurrentMission(USER_ID);
        UserMission saved = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(rejected.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(rejected.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_REJECTED);
        assertThat(rejected.getRejectedAt()).isNotBlank();
        assertThat(rejected.getCharacterMessage()).isNotBlank();
        assertThat(current.hasMission()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(UserMissionStatus.REJECTED);
        assertThat(saved.getRejectedAt()).isNotNull();
    }

    @Test
    void 거절_이유가_없으면_JUST_SKIP으로_피드백을_저장한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        missionService.rejectMission(USER_ID, created.getMission().getId());

        MissionFeedback feedback = findFeedback(created.getMission().getId(), MissionFeedbackType.REJECTION);
        assertThat(feedback.getReasonCode()).isEqualTo(MissionFeedbackReasonCode.JUST_SKIP);
        assertThat(feedback.getReasonText()).isNull();
        assertThat(userMemoryRepository.findBySourceTypeAndSourceIdAndMemoryType(
                UserMemorySourceType.MISSION_FEEDBACK,
                feedback.getId(),
                UserMemoryType.MISSION_REJECTION
        )).isEmpty();
    }

    @Test
    void 거절_이유와_사유를_선택적으로_저장한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        missionService.rejectMission(
                USER_ID,
                created.getMission().getId(),
                "TOO_HARD",
                "오늘은 몸이 무거워"
        );

        MissionFeedback feedback = findFeedback(created.getMission().getId(), MissionFeedbackType.REJECTION);
        UserMemory memory = findMemory(
                UserMemorySourceType.MISSION_FEEDBACK,
                feedback.getId(),
                UserMemoryType.MISSION_REJECTION
        );
        assertThat(feedback.getReasonCode()).isEqualTo(MissionFeedbackReasonCode.TOO_HARD);
        assertThat(feedback.getReasonText()).isEqualTo("오늘은 몸이 무거워");
        assertThat(memory.getContent()).contains("오늘은 몸이 무거워", "TOO_HARD");
        assertThat(memory.getMetadataJson().get("missionId").asLong()).isEqualTo(created.getMission().getId());
        assertThat(memory.getMetadataJson().get("reasonCode").asText()).isEqualTo("TOO_HARD");
    }

    @Test
    void 오늘_미션_stack은_완료_거절_진행중_미션과_집계를_함께_반환한다() {
        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, first.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, first.getMission().getId(), "첫 번째 미션 완료");

        CreateNextMissionResponse second = missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());
        missionService.rejectMission(USER_ID, second.getMission().getId());

        CreateNextMissionResponse third = missionService.createNextMission(USER_ID, CHARACTER_ID, second.getMission().getId());

        GetTodayMissionsResponse response = missionService.getTodayMissions(USER_ID);

        assertThat(response.getMissionDate()).isNotBlank();
        assertThat(response.getMaxDailyOffers()).isEqualTo(20);
        assertThat(response.getMaxDailyRewardCount()).isEqualTo(20);
        assertThat(response.getOfferedCount()).isEqualTo(3);
        assertThat(response.getCompletedCount()).isEqualTo(1);
        assertThat(response.getRejectedCount()).isEqualTo(1);
        assertThat(response.getRemainingOfferCount()).isEqualTo(19);
        assertThat(response.getRemainingRewardCount()).isEqualTo(19);
        assertThat(response.getMaxDailyRejectCount()).isEqualTo(10);
        assertThat(response.getRemainingRejectCount()).isEqualTo(9);
        assertThat(response.hasCurrentMissionId()).isTrue();
        assertThat(response.getCurrentMissionId()).isEqualTo(third.getMission().getId());
        assertThat(response.getMissionsList()).hasSize(3);
        assertThat(response.getMissions(0).getId()).isEqualTo(first.getMission().getId());
        assertThat(response.getMissions(0).getStackOrder()).isEqualTo(1);
        assertThat(response.getMissions(0).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getMissions(0).getCompletedAt()).isNotBlank();
        assertThat(response.getMissions(0).getCompletionQuestion()).isEqualTo("AI가 만든 완료 질문");
        assertThat(response.getMissions(0).getAnswerPreview()).isEqualTo("첫 번째 미션 완료");
        assertThat(response.getMissions(0).getHasAnswer()).isTrue();
        assertThat(response.getMissions(1).getId()).isEqualTo(second.getMission().getId());
        assertThat(response.getMissions(1).getStackOrder()).isEqualTo(2);
        assertThat(response.getMissions(1).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_REJECTED);
        assertThat(response.getMissions(1).getRejectedAt()).isNotBlank();
        assertThat(response.getMissions(1).getHasAnswer()).isFalse();
        assertThat(response.getMissions(2).getId()).isEqualTo(third.getMission().getId());
        assertThat(response.getMissions(2).getStackOrder()).isEqualTo(3);
        assertThat(response.getMissions(2).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_OFFERED);
        assertThat(response.getMissions(2).getCompletedAt()).isBlank();
        assertThat(response.getMissions(2).getRejectedAt()).isBlank();
    }

    @Test
    void 하루_미션_보상은_완료_20회까지만_가능하다() {
        for (int i = 0; i < 20; i++) {
            CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
            missionService.startCompletionSession(USER_ID, created.getMission().getId());
            missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료 " + i);
        }

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void 거절한_미션은_하루_보상_횟수를_차감하지_않는다() {
        for (int i = 0; i < 2; i++) {
            CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
            missionService.rejectMission(USER_ID, created.getMission().getId());
        }

        GetTodayMissionsResponse response = missionService.getTodayMissions(USER_ID);

        assertThat(response.getCompletedRewardCount()).isZero();
        assertThat(response.getRejectedCount()).isEqualTo(2);
        assertThat(response.getRemainingRewardCount()).isEqualTo(20);
        assertThat(response.getRemainingOfferCount()).isEqualTo(20);
        assertThat(response.getRemainingRejectCount()).isEqualTo(8);
    }

    @Test
    void 날짜별_미션_조회는_요청한_날짜의_목록만_반환한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        LocalDate today = LocalDate.parse(missionService.getTodayMissions(USER_ID).getMissionDate());
        LocalDate yesterday = today.minusDays(1);

        GetTodayMissionsResponse todayResponse = missionService.getTodayMissions(USER_ID, today);
        GetTodayMissionsResponse yesterdayResponse = missionService.getTodayMissions(USER_ID, yesterday);

        assertThat(todayResponse.getMissionDate()).isEqualTo(today.toString());
        assertThat(todayResponse.getMissionsList())
                .extracting("id")
                .containsExactly(created.getMission().getId());
        assertThat(yesterdayResponse.getMissionDate()).isEqualTo(yesterday.toString());
        assertThat(yesterdayResponse.getMissionsList()).isEmpty();
    }

    @Test
    void 미션_상세_조회는_완료_질문과_답변_전문을_반환한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "오늘은 책상 위 컵을 치우고 물도 한 모금 마셨어"
        );

        var response = missionService.getMissionDetail(USER_ID, created.getMission().getId());

        assertThat(response.getMission().getId()).isEqualTo(created.getMission().getId());
        assertThat(response.getMission().getQuestion().getText()).isEqualTo("AI가 만든 완료 질문");
        assertThat(response.getMission().getHasAnswer()).isTrue();
        assertThat(response.getMission().getAnswer().getText())
                .isEqualTo("오늘은 책상 위 컵을 치우고 물도 한 모금 마셨어");
        assertThat(response.getMission().getAnswer().getAnsweredAt()).isNotBlank();
        assertThat(response.getMission().getCompletionCharacterResponse()).isEqualTo("AI가 만든 완료 반응");
        assertThat(response.getMission().hasSatisfactionFeedback()).isFalse();
    }

    @Test
    void 하루_미션_거절은_10회까지만_가능하고_초과하면_현재_미션을_유지한다() {
        for (int i = 0; i < 10; i++) {
            CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
            missionService.rejectMission(USER_ID, created.getMission().getId());
        }
        CreateNextMissionResponse current = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.rejectMission(USER_ID, current.getMission().getId()))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REJECT_LIMIT_EXCEEDED);

        UserMission saved = userMissionRepository.findById(current.getMission().getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserMissionStatus.OFFERED);
    }

    @Test
    void OFFERED_미션은_완료_질문_세션을_시작할_수_있다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        StartCompletionSessionResponse response = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();

        assertThat(response.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_ANSWERING);
        assertThat(response.getQuestion().getId()).isEqualTo(savedAnswer.getId());
        assertThat(response.getQuestion().getText()).isEqualTo("AI가 만든 완료 질문");
        assertThat(response.getQuestion().getInputType()).isEqualTo(CompletionInputType.COMPLETION_INPUT_TYPE_TEXT);
        assertThat(response.getQuestion().getMinLength()).isEqualTo(1);
        assertThat(response.getQuestion().getMaxLength()).isEqualTo(300);
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.ANSWERING);
        assertThat(savedMission.getCompletionStartedAt()).isNotNull();
        assertThat(savedAnswer.getAnswerText()).isNull();
        assertThat(savedAnswer.getAnsweredAt()).isNull();
    }

    @Test
    void ANSWERING_미션에_완료_질문_세션을_다시_시작하면_기존_질문을_반환한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        StartCompletionSessionResponse first = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );

        StartCompletionSessionResponse second = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );

        assertThat(second.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_ANSWERING);
        assertThat(second.getQuestion().getId()).isEqualTo(first.getQuestion().getId());
        assertThat(missionCompletionAnswerRepository.count()).isEqualTo(1);
    }

    @Test
    void ANSWERING_미션은_거절한_뒤_다음_미션을_받을_수_있다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());

        RejectMissionResponse rejected = missionService.rejectMission(
                USER_ID,
                created.getMission().getId()
        );
        GetCurrentMissionResponse current = missionService.getCurrentMission(USER_ID);
        UserMission saved = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        CreateNextMissionResponse next = missionService.createNextMission(
                USER_ID,
                CHARACTER_ID,
                created.getMission().getId()
        );

        assertThat(rejected.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_REJECTED);
        assertThat(current.hasMission()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(UserMissionStatus.REJECTED);
        assertThat(saved.getRejectedAt()).isNotNull();
        assertThat(next.getMission().getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_OFFERED);
        assertThat(next.getMission().getStackOrder()).isEqualTo(2);
    }

    @Test
    void REJECTED_미션은_완료_질문_세션을_시작할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.rejectMission(USER_ID, created.getMission().getId());

        assertThatThrownBy(() -> missionService.startCompletionSession(USER_ID, created.getMission().getId()))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_INVALID_STATUS);
    }

    @Test
    void ANSWERING_미션은_답변_제출_후_COMPLETED_상태가_된다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "  물 한 컵을 마셨어  "
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());
        MissionOutboxEvent savedCharacterExpOutbox = findCharacterExpOutbox(created.getMission().getId());

        assertThat(response.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getAnswer().getText()).isEqualTo("물 한 컵을 마셨어");
        assertThat(response.getAnswer().getAnsweredAt()).isNotBlank();
        assertThat(response.getReward().getStarPiece()).isEqualTo(10);
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PAID);
        assertThat(response.hasWallet()).isTrue();
        assertThat(response.getWallet().getStarPiece()).isEqualTo(110);
        assertThat(response.hasCharacterExp()).isTrue();
        assertThat(response.getCharacterExp().getExpAmount()).isEqualTo(10);
        assertThat(response.getCharacterExp().getExpGained()).isEqualTo(10);
        assertThat(response.getCharacterExp().getStatus()).isEqualTo(MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_APPLIED);
        assertThat(response.getCharacterExp().getAfterGrowth().getExp()).isEqualTo(10);
        assertThat(response.getCharacterMessage()).isNotBlank();
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getCompletedAt()).isNotNull();
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedAnswer.getAnswerText()).isEqualTo("물 한 컵을 마셨어");
        assertThat(savedAnswer.getAnsweredAt()).isNotNull();
        UserMemory memory = findMemory(
                UserMemorySourceType.MISSION_COMPLETION_ANSWER,
                savedAnswer.getId(),
                UserMemoryType.MISSION_COMPLETION
        );
        assertThat(memory.getContent()).contains("물 한 컵을 마셨어");
        assertThat(memory.getImportance()).isEqualTo(70);
        assertThat(memory.getMetadataJson().get("missionId").asLong()).isEqualTo(created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isZero();
        assertThat(savedOutbox.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedCharacterExpOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        assertThat(savedCharacterExpOutbox.getAttemptCount()).isZero();
        assertThat(savedCharacterExpOutbox.getIdempotencyKey()).isEqualTo("MISSION_CHARACTER_EXP:" + created.getMission().getId());
        verify(walletRewardClient).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
        verify(characterExpClient).grantMissionCompletionExp(
                USER_ID,
                CHARACTER_ID,
                created.getMission().getId(),
                10,
                "MISSION_CHARACTER_EXP:" + created.getMission().getId()
        );
        verify(notificationPushClient, never()).sendMissionRewardRecoveredNotification(
                USER_ID,
                created.getMission().getId(),
                10
        );
    }

    @Test
    void 완료_미션에_만족도_피드백을_저장하고_다시_보내면_갱신한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        UpsertMissionFeedbackResponse liked = missionService.upsertMissionFeedback(
                USER_ID,
                created.getMission().getId(),
                MissionFeedbackType.SATISFACTION,
                MissionFeedbackReaction.LIKE,
                null,
                null
        );
        UpsertMissionFeedbackResponse disliked = missionService.upsertMissionFeedback(
                USER_ID,
                created.getMission().getId(),
                MissionFeedbackType.SATISFACTION,
                MissionFeedbackReaction.DISLIKE,
                null,
                null
        );

        MissionFeedback feedback = findFeedback(created.getMission().getId(), MissionFeedbackType.SATISFACTION);
        UserMemory memory = findMemory(
                UserMemorySourceType.MISSION_FEEDBACK,
                feedback.getId(),
                UserMemoryType.MISSION_SATISFACTION
        );
        assertThat(liked.getReaction().name()).isEqualTo("MISSION_FEEDBACK_REACTION_LIKE");
        assertThat(disliked.getReaction().name()).isEqualTo("MISSION_FEEDBACK_REACTION_DISLIKE");
        assertThat(feedback.getReaction()).isEqualTo(MissionFeedbackReaction.DISLIKE);
        assertThat(missionService.getMissionDetail(USER_ID, created.getMission().getId())
                .getMission()
                .getSatisfactionFeedback()
                .getReaction()
                .name()).isEqualTo("MISSION_FEEDBACK_REACTION_DISLIKE");
        assertThat(memory.getContent()).contains("별로였어요");
        assertThat(memory.getImportance()).isEqualTo(75);
        assertThat(memory.getMetadataJson().get("reaction").asText()).isEqualTo("DISLIKE");
        assertThat(missionFeedbackRepository.findAll())
                .filteredOn(saved -> saved.getMissionId().equals(created.getMission().getId())
                        && saved.getFeedbackType() == MissionFeedbackType.SATISFACTION)
                .hasSize(1);
        assertThat(userMemoryRepository.findAll())
                .filteredOn(saved -> saved.getSourceType() == UserMemorySourceType.MISSION_FEEDBACK
                        && saved.getSourceId().equals(feedback.getId())
                        && saved.getMemoryType() == UserMemoryType.MISSION_SATISFACTION)
                .hasSize(1);
    }

    @Test
    void 다른_유저의_미션에는_만족도_피드백을_저장할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        assertThatThrownBy(() -> missionService.upsertMissionFeedback(
                9999L,
                created.getMission().getId(),
                MissionFeedbackType.SATISFACTION,
                MissionFeedbackReaction.LIKE,
                null,
                null
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_NOT_FOUND);
    }

    @Test
    void 완료되지_않은_미션에는_만족도_피드백을_저장할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.upsertMissionFeedback(
                USER_ID,
                created.getMission().getId(),
                MissionFeedbackType.SATISFACTION,
                MissionFeedbackReaction.LIKE,
                null,
                null
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_FEEDBACK_INVALID);
    }

    @Test
    void 완료_답변은_1자_이상_300자_이하여야_한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "   "
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ANSWER_INVALID);

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "가".repeat(301)
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ANSWER_INVALID);
    }

    @Test
    void COMPLETED_미션에_같은_답변을_다시_제출하면_wallet_적립을_다시_호출하지_않는다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                " 완료했어 "
        );

        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getAnswer().getText()).isEqualTo("완료했어");
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PAID);
        assertThat(response.hasWallet()).isTrue();
        assertThat(response.getWallet().getStarPiece()).isEqualTo(110);
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        verify(walletRewardClient, times(1)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
        verify(walletRewardClient, times(1)).getWalletStarPiece(USER_ID);
    }

    @Test
    void COMPLETED_미션에_다른_답변을_다시_제출하면_수정할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "또 완료했어"
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ALREADY_COMPLETED);
    }

    @Test
    void wallet_보상_지급이_실패하면_미션은_COMPLETED지만_보상_marker는_남기지_않는다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());

        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        assertThat(response.hasWallet()).isFalse();
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getIdempotencyKey()).isNull();
        assertThat(savedAnswer.getAnswerText()).isEqualTo("완료했어");
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(savedOutbox.getLastErrorMessage()).isNotBlank();
    }

    @Test
    void reward_marker가_없는_COMPLETED_미션은_같은_답변_재시도_시_PENDING을_반환한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));

        SubmitCompletionAnswerResponse first = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );

        SubmitCompletionAnswerResponse retried = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());

        assertThat(first.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        assertThat(first.hasWallet()).isFalse();
        assertThat(retried.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(retried.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        assertThat(retried.hasWallet()).isFalse();
        assertThat(savedMission.getIdempotencyKey()).isNull();
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        verify(walletRewardClient, times(1)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
        verify(notificationPushClient, never()).sendMissionRewardRecoveredNotification(
                USER_ID,
                created.getMission().getId(),
                10
        );
    }

    @Test
    void 캐릭터_경험치_지급이_실패해도_미션은_COMPLETED이고_경험치는_PENDING으로_남긴다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(characterExpClient.grantMissionCompletionExp(anyLong(), anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED));

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );

        MissionOutboxEvent expOutbox = findCharacterExpOutbox(created.getMission().getId());
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PAID);
        assertThat(response.hasCharacterExp()).isTrue();
        assertThat(response.getCharacterExp().getExpAmount()).isEqualTo(10);
        assertThat(response.getCharacterExp().getExpGained()).isZero();
        assertThat(response.getCharacterExp().getStatus()).isEqualTo(MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_PENDING);
        assertThat(expOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        assertThat(expOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
    }

    @Test
    void 캐릭터_경험치_outbox_스케줄러는_PENDING_경험치를_같은_멱등키로_재처리한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(characterExpClient.grantMissionCompletionExp(anyLong(), anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED))
                .thenAnswer(invocation -> {
                    Long characterId = invocation.getArgument(1);
                    int expAmount = invocation.getArgument(3);
                    return new CharacterExpGrantResult(
                            characterId,
                            expAmount,
                            characterGrowth(0),
                            characterGrowth(expAmount),
                            false,
                            false
                    );
                });

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );
        assertThat(response.getCharacterExp().getStatus()).isEqualTo(MissionCharacterExpStatus.MISSION_CHARACTER_EXP_STATUS_PENDING);

        MissionOutboxEvent pendingOutbox = findCharacterExpOutbox(created.getMission().getId());
        ReflectionTestUtils.setField(pendingOutbox, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        missionOutboxEventRepository.saveAndFlush(pendingOutbox);

        int succeededCount = missionCharacterExpDispatcher.dispatchDue(10);

        MissionOutboxEvent savedOutbox = findCharacterExpOutbox(created.getMission().getId());
        assertThat(succeededCount).isEqualTo(1);
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        verify(characterExpClient, times(2)).grantMissionCompletionExp(
                USER_ID,
                CHARACTER_ID,
                created.getMission().getId(),
                10,
                "MISSION_CHARACTER_EXP:" + created.getMission().getId()
        );
    }

    @Test
    void 보상_outbox_스케줄러는_PENDING_보상을_같은_멱등키로_재처리한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .thenReturn(new WalletRewardResult(110, 9001L));

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        assertThat(response.hasWallet()).isFalse();

        MissionOutboxEvent pendingOutbox = findRewardOutbox(created.getMission().getId());
        ReflectionTestUtils.setField(pendingOutbox, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        missionOutboxEventRepository.saveAndFlush(pendingOutbox);

        int succeededCount = missionRewardDispatcher.dispatchDue(10);

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());
        assertThat(succeededCount).isEqualTo(1);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        verify(walletRewardClient, times(2)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
        verify(notificationPushClient).sendMissionRewardRecoveredNotification(
                USER_ID,
                created.getMission().getId(),
                10
        );
    }

    @Test
    void 보상_outbox_재처리_성공_알림이_실패해도_보상_성공은_유지된다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .thenReturn(new WalletRewardResult(110, 9001L));
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationPushClient)
                .sendMissionRewardRecoveredNotification(USER_ID, created.getMission().getId(), 10);

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );
        assertThat(response.getRewardStatus()).isEqualTo(MissionRewardStatus.MISSION_REWARD_STATUS_PENDING);
        assertThat(response.hasWallet()).isFalse();

        MissionOutboxEvent pendingOutbox = findRewardOutbox(created.getMission().getId());
        ReflectionTestUtils.setField(pendingOutbox, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        missionOutboxEventRepository.saveAndFlush(pendingOutbox);

        int succeededCount = missionRewardDispatcher.dispatchDue(10);

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionOutboxEvent savedOutbox = findRewardOutbox(created.getMission().getId());
        assertThat(succeededCount).isEqualTo(1);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionOutboxEventStatus.SUCCEEDED);
        verify(notificationPushClient).sendMissionRewardRecoveredNotification(
                USER_ID,
                created.getMission().getId(),
                10
        );

        // Prometheus Counter 및 Gauge 검증
        var counter = meterRegistry.find("outbox.events.processed")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isPositive();
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("MISSION");

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isGreaterThanOrEqualTo(0.0);
    }

    private MissionOutboxEvent findRewardOutbox(Long missionId) {
        return missionOutboxEventRepository
                .findByIdempotencyKey("MISSION_REWARD:" + missionId)
                .orElseThrow();
    }

    private MissionOutboxEvent findCharacterExpOutbox(Long missionId) {
        return missionOutboxEventRepository
                .findByIdempotencyKey("MISSION_CHARACTER_EXP:" + missionId)
                .orElseThrow();
    }

    private MissionFeedback findFeedback(Long missionId, MissionFeedbackType feedbackType) {
        return missionFeedbackRepository
                .findByUserIdAndMissionIdAndFeedbackType(USER_ID, missionId, feedbackType)
                .orElseThrow();
    }

    private UserMemory findMemory(
            UserMemorySourceType sourceType,
            Long sourceId,
            UserMemoryType memoryType
    ) {
        return userMemoryRepository
                .findBySourceTypeAndSourceIdAndMemoryType(sourceType, sourceId, memoryType)
                .orElseThrow();
    }

    private String differentCategory(String category) {
        return "BASIC_ROUTINE".equals(category) ? "REST_RECOVERY" : "BASIC_ROUTINE";
    }

    private MissionCharacterGrowth characterGrowth(int exp) {
        return new MissionCharacterGrowth(
                exp >= 200 ? 2 : 1,
                exp,
                exp >= 200 ? 200 : 0,
                exp >= 200 ? 600 : 200,
                exp >= 200 ? Math.max(0, 600 - exp) : Math.max(0, 200 - exp),
                exp >= 200 ? Math.min(100, (exp - 200) * 100 / 400) : Math.min(100, exp * 100 / 200),
                exp >= 200 ? "GROWING" : "BABY",
                exp >= 200 ? "자라나는 별친구" : "작은 별씨",
                false
        );
    }

    private AiMissionTextResult aiMissionTextResult(
            Long aiGenerationId,
            String title,
            String description,
            String category,
            String difficulty
    ) {
        return aiMissionTextResult(aiGenerationId, title, description, category, difficulty, false);
    }

    private AiMissionTextResult aiMissionTextResult(
            Long aiGenerationId,
            String title,
            String description,
            String category,
            String difficulty,
            boolean fallbackUsed
    ) {
        return new AiMissionTextResult(
                aiGenerationId,
                title,
                description,
                "AI가 바꾼 제안 문구",
                "AI가 만든 완료 질문",
                "AI가 만든 완료 반응",
                category,
                difficulty,
                fallbackUsed,
                "request-" + aiGenerationId
        );
    }

    private Clock fixedClockAt(int year, int month, int day, int hour, int minute) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return Clock.fixed(
                LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant(),
                zone
        );
    }
}
