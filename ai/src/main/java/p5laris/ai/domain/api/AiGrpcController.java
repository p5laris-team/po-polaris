package p5laris.ai.domain.api;

import com.p5laris.proto.ai.v1.AiErrorType;
import com.p5laris.proto.ai.v1.AiGenerationStatus;
import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.CharacterTalkStreamEventType;
import com.p5laris.proto.ai.v1.GenerateMissionTextsRequest;
import com.p5laris.proto.ai.v1.GenerateMissionTextsResponse;
import com.p5laris.proto.ai.v1.GenerateTextEmbeddingRequest;
import com.p5laris.proto.ai.v1.GenerateTextEmbeddingResponse;
import com.p5laris.proto.ai.v1.HealthStatus;
import com.p5laris.proto.ai.v1.PingPongRequest;
import com.p5laris.proto.ai.v1.PingPongResponse;
import com.p5laris.proto.ai.v1.StreamCharacterTalkRequest;
import com.p5laris.proto.ai.v1.StreamCharacterTalkResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.ai.domain.application.AiCharacterTalkService;
import p5laris.ai.domain.application.AiMissionTextService;
import p5laris.ai.domain.application.AiTextEmbeddingService;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingResult;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;

/**
 * AI 모듈의 gRPC 진입점이다.
 *
 * proto message를 application service가 이해하는 command로 바꾸고,
 * application 결과를 다시 proto response로 변환한다.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AiGrpcController extends AiServiceGrpc.AiServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "AI 서비스 처리 중 오류가 발생했습니다.";

    private final AiMissionTextService aiMissionTextService;
    private final AiTextEmbeddingService aiTextEmbeddingService;
    private final AiCharacterTalkService aiCharacterTalkService;

    // AI gRPC 서버가 살아 있는지 확인하는 단순 헬스체크 메서드다.
    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // 개인화 context를 바탕으로 AI 자율 미션 후보를 생성하는 gRPC 메서드다.
    @Override
    public void generateMissionTexts(
            GenerateMissionTextsRequest request,
            StreamObserver<GenerateMissionTextsResponse> responseObserver
    ) {
        try {
            MissionTextGenerationResult result = aiMissionTextService.generateMissionTexts(toCommand(request));
            responseObserver.onNext(toResponse(result));
            responseObserver.onCompleted();
        } catch (AiException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            log.error("AI gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", "generateMissionTexts", e);
            responseObserver.onError(Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException());
        }
    }

    // 사용자 기억 RAG 검색에 사용할 text embedding을 생성한다.
    @Override
    public void generateTextEmbedding(
            GenerateTextEmbeddingRequest request,
            StreamObserver<GenerateTextEmbeddingResponse> responseObserver
    ) {
        try {
            TextEmbeddingResult result = aiTextEmbeddingService.generateTextEmbedding(toCommand(request));
            responseObserver.onNext(toResponse(result));
            responseObserver.onCompleted();
        } catch (AiException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            log.error("AI gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", "generateTextEmbedding", e);
            responseObserver.onError(Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException());
        }
    }

    // 별친구 대화 응답을 provider streaming으로 생성해 chunk 단위로 내려준다.
    @Override
    public void streamCharacterTalk(
            StreamCharacterTalkRequest request,
            StreamObserver<StreamCharacterTalkResponse> responseObserver
    ) {
        try {
            aiCharacterTalkService.streamCharacterTalk(toCommand(request), new GrpcCharacterTalkStreamEmitter(
                    request.getRequestId(),
                    responseObserver
            ));
            responseObserver.onCompleted();
        } catch (AiException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            log.error("AI gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", "streamCharacterTalk", e);
            responseObserver.onError(Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException());
        }
    }

    // proto request는 내부 계층에 직접 넘기지 않고 command로 변환한다.
    private MissionTextGenerationCommand toCommand(GenerateMissionTextsRequest request) {
        return new MissionTextGenerationCommand(
                request.getUserId(),
                request.getCharacterId(),
                request.getCharacterType(),
                request.getMissionTemplateId(),
                request.getBaseTitle(),
                request.getBaseDescription(),
                request.getCategory(),
                request.getDifficulty(),
                request.getFallbackCharacterMessage(),
                request.getFallbackQuestion(),
                request.getFallbackCompletionResponse(),
                request.getOnboardingContextJson(),
                request.getRecentMissionContextJson(),
                request.getRequestId()
        );
    }

    private TextEmbeddingCommand toCommand(GenerateTextEmbeddingRequest request) {
        return new TextEmbeddingCommand(
                request.getUserId(),
                request.getText(),
                request.getModel(),
                request.getDimension(),
                request.getRequestId()
        );
    }

    private CharacterTalkGenerationCommand toCommand(StreamCharacterTalkRequest request) {
        return new CharacterTalkGenerationCommand(
                request.getUserId(),
                request.getCharacterId(),
                request.getCharacterType(),
                request.getCharacterName(),
                request.getUserMessage(),
                request.getInteractionType(),
                request.getCharacterContextJson(),
                request.getRequestId()
        );
    }

    // application result를 gRPC 응답 계약에 맞는 proto response로 변환한다.
    private GenerateMissionTextsResponse toResponse(MissionTextGenerationResult result) {
        GenerateMissionTextsResponse.Builder builder = GenerateMissionTextsResponse.newBuilder()
                .setAiGenerationId(result.aiGenerationId())
                .setStatus(toProtoStatus(result.status()))
                .setTitle(result.title())
                .setDescription(result.description())
                .setCharacterMessage(result.characterMessage())
                .setCompletionQuestion(result.completionQuestion())
                .setCompletionCharacterResponse(result.completionCharacterResponse())
                .setCategory(result.category())
                .setDifficulty(result.difficulty())
                .setFallbackUsed(result.fallbackUsed())
                .setRequestId(result.requestId());

        if (result.errorType() != null) {
            builder.setErrorType(toProtoErrorType(result.errorType()));
        }

        return builder.build();
    }

    private GenerateTextEmbeddingResponse toResponse(TextEmbeddingResult result) {
        return GenerateTextEmbeddingResponse.newBuilder()
                .setModel(result.model())
                .setDimension(result.dimension())
                .addAllValues(result.values())
                .setRequestId(result.requestId())
                .build();
    }

    // 내부 enum과 proto enum은 타입이 다르므로 명시적으로 매핑한다.
    private AiGenerationStatus toProtoStatus(p5laris.ai.domain.domain.enums.AiGenerationStatus status) {
        return switch (status) {
            case SUCCESS -> AiGenerationStatus.AI_GENERATION_STATUS_SUCCESS;
            case FALLBACK -> AiGenerationStatus.AI_GENERATION_STATUS_FALLBACK;
            case FAILED -> AiGenerationStatus.AI_GENERATION_STATUS_FAILED;
        };
    }

    // AI 실패 원인도 proto enum으로 변환해 호출자가 fallback 원인을 알 수 있게 한다.
    private AiErrorType toProtoErrorType(p5laris.ai.domain.domain.enums.AiErrorType errorType) {
        return switch (errorType) {
            case TIMEOUT -> AiErrorType.AI_ERROR_TYPE_TIMEOUT;
            case RATE_LIMIT -> AiErrorType.AI_ERROR_TYPE_RATE_LIMIT;
            case RATE_LIMIT_UNAVAILABLE -> AiErrorType.AI_ERROR_TYPE_RATE_LIMIT_UNAVAILABLE;
            case INVALID_OUTPUT -> AiErrorType.AI_ERROR_TYPE_INVALID_OUTPUT;
            case POLICY_VIOLATION -> AiErrorType.AI_ERROR_TYPE_POLICY_VIOLATION;
            case PROVIDER_ERROR -> AiErrorType.AI_ERROR_TYPE_PROVIDER_ERROR;
            case UNKNOWN -> AiErrorType.AI_ERROR_TYPE_UNKNOWN;
        };
    }

    // AI 도메인 예외를 gateway가 해석 가능한 gRPC status로 변환한다.
    private Status toStatus(AiException e) {
        AiErrorCode errorCode = e.getErrorCode();

        return switch (errorCode) {
            case AI_INVALID_REQUEST -> Status.INVALID_ARGUMENT;
            case AI_DUPLICATED_REQUEST, AI_REQUEST_CONFLICT -> Status.ALREADY_EXISTS;
            case AI_FALLBACK_INVALID -> Status.FAILED_PRECONDITION;
            case AI_GENERATION_FAILED, AI_EMBEDDING_FAILED -> Status.INTERNAL;
        };
    }

    private String safeDescription(AiException e) {
        return e.getErrorCode().name();
    }

    private class GrpcCharacterTalkStreamEmitter implements CharacterTalkStreamEmitter {

        private final String requestId;
        private final StreamObserver<StreamCharacterTalkResponse> responseObserver;
        private boolean emittedDelta;

        private GrpcCharacterTalkStreamEmitter(
                String requestId,
                StreamObserver<StreamCharacterTalkResponse> responseObserver
        ) {
            this.requestId = requestId;
            this.responseObserver = responseObserver;
        }

        @Override
        public void emitDelta(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            responseObserver.onNext(StreamCharacterTalkResponse.newBuilder()
                    .setEventType(CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_DELTA)
                    .setText(text)
                    .setRequestId(requestId)
                    .build());
            emittedDelta = true;
        }

        @Override
        public void complete(boolean fallbackUsed, p5laris.ai.domain.domain.enums.AiErrorType errorType) {
            StreamCharacterTalkResponse.Builder builder = StreamCharacterTalkResponse.newBuilder()
                    .setEventType(CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_DONE)
                    .setFallbackUsed(fallbackUsed)
                    .setRequestId(requestId);

            if (errorType != null) {
                builder.setErrorType(toProtoErrorType(errorType));
            }

            responseObserver.onNext(builder.build());
        }

        @Override
        public boolean hasEmittedDelta() {
            return emittedDelta;
        }
    }
}
