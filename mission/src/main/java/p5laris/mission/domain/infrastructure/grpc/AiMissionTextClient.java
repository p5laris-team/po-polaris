package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.GenerateMissionTextsRequest;
import com.p5laris.proto.ai.v1.GenerateMissionTextsResponse;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionAiTextProperties;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * mission 모듈에서 ai 모듈의 GenerateMissionTexts gRPC를 호출하는 adapter다.
 *
 * AI 자율 미션 후보 생성은 사용자 흐름을 보조하는 기능이므로, 호출 실패를 mission 생성 실패로 전파하지 않는다.
 * 실패하면 Optional.empty()를 반환하고 MissionService가 seed template fallback 미션으로 계속 진행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMissionTextClient {

    private final MissionAiTextProperties properties;

    @GrpcClient("ai")
    private AiServiceGrpc.AiServiceBlockingStub aiStub;

    /**
     * 개인화 기반 자율 미션 후보 생성을 요청한다.
     *
     * requestId는 ai 모듈에서 멱등키처럼 사용되므로 같은 미션 후보 생성 시도에는 같은 값이 들어가야 한다.
     */
    public Optional<AiMissionTextResult> generateMissionTexts(AiMissionTextRequest request) {
        try {
            GenerateMissionTextsResponse response = deadlineAiStub().generateMissionTexts(
                    GenerateMissionTextsRequest.newBuilder()
                            .setUserId(request.userId())
                            .setCharacterId(request.characterId())
                            .setCharacterType(request.characterType())
                            .setCharacterName(request.characterName())
                            .setMissionTemplateId(request.missionTemplateId())
                            .setBaseTitle(request.baseTitle())
                            .setBaseDescription(request.baseDescription())
                            .setCategory(request.category())
                            .setDifficulty(request.difficulty())
                            .setFallbackCharacterMessage(request.fallbackCharacterMessage())
                            .setFallbackQuestion(request.fallbackQuestion())
                            .setFallbackCompletionResponse(request.fallbackCompletionResponse())
                            .setOnboardingContextJson(request.onboardingContextJson())
                            .setRecentMissionContextJson(request.recentMissionContextJson())
                            .setRequestId(request.requestId())
                            .build()
            );

            return Optional.of(new AiMissionTextResult(
                    response.getAiGenerationId(),
                    response.getTitle(),
                    response.getDescription(),
                    response.getCharacterMessage(),
                    response.getCompletionQuestion(),
                    response.getCompletionCharacterResponse(),
                    response.getCategory(),
                    response.getDifficulty(),
                    response.getFallbackUsed(),
                    response.getRequestId()
            ));
        } catch (StatusRuntimeException e) {
            log.warn("AI 자율 미션 후보 생성 실패. requestId={}, status={}",
                    request.requestId(), e.getStatus().getCode(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI 자율 미션 후보 생성 실패. requestId={}", request.requestId(), e);
            return Optional.empty();
        }
    }

    private AiServiceGrpc.AiServiceBlockingStub deadlineAiStub() {
        return aiStub.withDeadlineAfter(properties.getDeadlineMs(), TimeUnit.MILLISECONDS);
    }
}
