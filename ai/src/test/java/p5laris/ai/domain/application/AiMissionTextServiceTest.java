package p5laris.ai.domain.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.repository.AiMissionGenerationRepository;
import p5laris.ai.domain.domain.repository.AiUsageLogRepository;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 외부 Gemini 응답은 네트워크와 rate limit에 영향을 받으므로, 서비스 통합 테스트는 rule-based 경로로 고정한다.
@SpringBootTest(properties = {
        "grpc.server.port=0",
        "ai.provider.enabled=false",
        "spring.ai.model.embedding.text=none",
        "ai.embedding.enabled=false",
        "ai.embedding.model=gemini-embedding-001",
        "ai.embedding.dimension=768",
        "ai.circuit-breaker.enabled=false",
        "ai.circuit-breaker.sliding-window-size=10",
        "ai.circuit-breaker.minimum-number-of-calls=5",
        "ai.circuit-breaker.failure-rate-threshold=50",
        "ai.circuit-breaker.slow-call-duration-ms=3000",
        "ai.circuit-breaker.slow-call-rate-threshold=50",
        "ai.circuit-breaker.wait-duration-open-ms=30000",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class AiMissionTextServiceTest {

    @Autowired
    private AiMissionTextService aiMissionTextService;

    @Autowired
    private AiMissionGenerationRepository aiMissionGenerationRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private PromptTemplateRepository promptTemplateRepository;

    @BeforeEach
    void setUp() {
        aiUsageLogRepository.deleteAll();
        aiMissionGenerationRepository.deleteAll();
    }

    @Test
    void 캐릭터_말투로_미션_문구를_생성하고_결과와_사용_로그를_저장한다() {
        MissionTextGenerationCommand command = validCommand("NOVA");

        MissionTextGenerationResult result = aiMissionTextService.generateMissionTexts(command);

        assertThat(result.status()).isEqualTo(AiGenerationStatus.SUCCESS);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.errorType()).isNull();
        assertThat(result.title()).isEqualTo(command.baseTitle());
        assertThat(result.description()).isEqualTo(command.baseDescription());
        assertThat(result.characterMessage()).contains("천천히");
        assertThat(result.completionQuestion()).isNotBlank();
        assertThat(result.completionCharacterResponse()).isNotBlank();
        assertThat(result.category()).isEqualTo(command.category());
        assertThat(result.difficulty()).isEqualTo(command.difficulty());
        assertThat(result.aiGenerationId()).isPositive();
        assertThat(aiMissionGenerationRepository.count()).isEqualTo(1);
        assertThat(aiUsageLogRepository.count()).isEqualTo(1);
        assertThat(aiUsageLogRepository.findByRequestId(command.requestId()).orElseThrow().getStatus())
                .isEqualTo(AiUsageStatus.SUCCESS);
        assertThat(promptTemplateRepository.findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory.MISSION_GENERATION))
                .isPresent();
    }

    @Test
    void 지원하지_않는_캐릭터_타입이면_fallback_문구를_저장하고_반환한다() {
        MissionTextGenerationCommand command = validCommand("UNKNOWN");

        MissionTextGenerationResult result = aiMissionTextService.generateMissionTexts(command);

        assertThat(result.status()).isEqualTo(AiGenerationStatus.FALLBACK);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.errorType()).isEqualTo(AiErrorType.INVALID_OUTPUT);
        assertThat(result.title()).isEqualTo(command.baseTitle());
        assertThat(result.description()).isEqualTo(command.baseDescription());
        assertThat(result.characterMessage()).isEqualTo(command.fallbackCharacterMessage());
        assertThat(result.completionQuestion()).isEqualTo(command.fallbackQuestion());
        assertThat(result.completionCharacterResponse()).isEqualTo(command.fallbackCompletionResponse());
        assertThat(result.category()).isEqualTo(command.category());
        assertThat(result.difficulty()).isEqualTo(command.difficulty());
        assertThat(aiMissionGenerationRepository.count()).isEqualTo(1);
        var savedGeneration = aiMissionGenerationRepository.findByRequestId(command.requestId()).orElseThrow();
        var usageLog = aiUsageLogRepository.findByRequestId(command.requestId()).orElseThrow();
        assertThat(savedGeneration.isFallbackUsed()).isTrue();
        assertThat(savedGeneration.getStatus()).isEqualTo(AiGenerationStatus.FALLBACK);
        assertThat(savedGeneration.getErrorType()).isEqualTo(AiErrorType.INVALID_OUTPUT);
        assertThat(savedGeneration.getRequestContextJson()).doesNotContain("rawPrompt");
        assertThat(savedGeneration.getResponseJson()).doesNotContain("rawResponse");
        assertThat(usageLog.getStatus()).isEqualTo(AiUsageStatus.FALLBACK);
        assertThat(usageLog.getErrorType()).isEqualTo(AiErrorType.INVALID_OUTPUT);
    }

    @Test
    void 무무_말투는_무_중심_문구와_해석을_함께_반환한다() {
        MissionTextGenerationCommand command = validCommand("MUMU");

        MissionTextGenerationResult result = aiMissionTextService.generateMissionTexts(command);

        assertThat(result.status()).isEqualTo(AiGenerationStatus.SUCCESS);
        assertMumuText(result.characterMessage());
        assertMumuText(result.completionQuestion());
        assertMumuText(result.completionCharacterResponse());
    }

    @Test
    void 무무_말투는_미션_문맥에_따라_여러_패턴으로_응답한다() {
        var characterMessages = LongStream.rangeClosed(3001L, 3012L)
                .mapToObj(templateId -> aiMissionTextService.generateMissionTexts(validCommand("MUMU", templateId)))
                .map(MissionTextGenerationResult::characterMessage)
                .collect(Collectors.toSet());

        assertThat(characterMessages).hasSizeGreaterThan(1);
        assertThat(characterMessages).allSatisfy(this::assertMumuText);
    }

    @Test
    void fallback_문구도_금지_표현을_포함하면_예외를_던지고_저장하지_않는다() {
        MissionTextGenerationCommand command = new MissionTextGenerationCommand(
                1001L,
                2001L,
                "UNKNOWN",
                3001L,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "한심하니까 지금 해.",
                "해보고 나서 어땠어?",
                "완료한 일을 기억할게.",
                "{}",
                "{}",
                UUID.randomUUID().toString()
        );

        assertThatThrownBy(() -> aiMissionTextService.generateMissionTexts(command))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_FALLBACK_INVALID);
        assertThat(aiMissionGenerationRepository.count()).isZero();
        assertThat(aiUsageLogRepository.count()).isZero();
    }

    @Test
    void 같은_requestId와_같은_요청이면_기존_생성_결과를_재사용한다() {
        MissionTextGenerationCommand command = validCommand("JJORY");
        MissionTextGenerationResult first = aiMissionTextService.generateMissionTexts(command);

        MissionTextGenerationResult second = aiMissionTextService.generateMissionTexts(command);

        assertThat(second.aiGenerationId()).isEqualTo(first.aiGenerationId());
        assertThat(second.title()).isEqualTo(first.title());
        assertThat(second.description()).isEqualTo(first.description());
        assertThat(second.characterMessage()).isEqualTo(first.characterMessage());
        assertThat(second.completionQuestion()).isEqualTo(first.completionQuestion());
        assertThat(second.completionCharacterResponse()).isEqualTo(first.completionCharacterResponse());
        assertThat(second.category()).isEqualTo(first.category());
        assertThat(second.difficulty()).isEqualTo(first.difficulty());
        assertThat(aiMissionGenerationRepository.count()).isEqualTo(1);
        assertThat(aiUsageLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_requestId인데_요청_내용이_다르면_충돌로_처리한다() {
        String requestId = UUID.randomUUID().toString();
        MissionTextGenerationCommand first = validCommand("JJORY", requestId);
        MissionTextGenerationCommand differentBody = new MissionTextGenerationCommand(
                first.userId(),
                first.characterId(),
                "NOVA",
                first.missionTemplateId(),
                first.baseTitle(),
                first.baseDescription(),
                first.category(),
                first.difficulty(),
                first.fallbackCharacterMessage(),
                first.fallbackQuestion(),
                first.fallbackCompletionResponse(),
                first.onboardingContextJson(),
                first.recentMissionContextJson(),
                requestId
        );
        aiMissionTextService.generateMissionTexts(first);

        assertThatThrownBy(() -> aiMissionTextService.generateMissionTexts(differentBody))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_REQUEST_CONFLICT);
        assertThat(aiMissionGenerationRepository.count()).isEqualTo(1);
        assertThat(aiUsageLogRepository.count()).isEqualTo(1);
    }

    private MissionTextGenerationCommand validCommand(String characterType) {
        return validCommand(characterType, UUID.randomUUID().toString());
    }

    private MissionTextGenerationCommand validCommand(String characterType, Long missionTemplateId) {
        return validCommand(characterType, UUID.randomUUID().toString(), missionTemplateId);
    }

    private MissionTextGenerationCommand validCommand(String characterType, String requestId) {
        return validCommand(characterType, requestId, 3001L);
    }

    private void assertMumuText(String text) {
        assertThat(text)
                .startsWith("무")
                .contains("(해석:")
                .contains("무무가")
                .endsWith(")");
    }

    private MissionTextGenerationCommand validCommand(String characterType, String requestId, Long missionTemplateId) {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                characterType,
                missionTemplateId,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
                "물 마시고 나서 기분이 조금 달라졌어?",
                "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
                "{\"routineGoal\":\"WAKE_UP\"}",
                "{\"recentRejected\":[]}",
                requestId
        );
    }
}
