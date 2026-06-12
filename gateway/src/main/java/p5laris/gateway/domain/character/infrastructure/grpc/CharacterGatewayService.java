package p5laris.gateway.domain.character.infrastructure.grpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.ai.v1.AiErrorType;
import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.CharacterTalkStreamEventType;
import com.p5laris.proto.ai.v1.GetCharacterTalkDiariesRequest;
import com.p5laris.proto.ai.v1.GetCharacterTalkDiariesResponse;
import com.p5laris.proto.ai.v1.GetCharacterTalkMessagesRequest;
import com.p5laris.proto.ai.v1.GetCharacterTalkMessagesResponse;
import com.p5laris.proto.ai.v1.StreamCharacterTalkRequest;
import com.p5laris.proto.ai.v1.StreamCharacterTalkResponse;
import com.p5laris.proto.character.v1.*;
import com.p5laris.proto.item.v1.UserItem;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import p5laris.gateway.domain.character.api.dto.CharacterTalkDiariesResponse;
import p5laris.gateway.domain.character.api.dto.CharacterTalkMessagesResponse;
import p5laris.gateway.domain.character.api.dto.CharacterTalkStreamRequest;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkAiProperties;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkContextProperties;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkSseProperties;
import p5laris.gateway.domain.character.infrastructure.limit.CharacterTalkLimitResult;
import p5laris.gateway.domain.character.infrastructure.limit.CharacterTalkLimitStatus;
import p5laris.gateway.domain.character.infrastructure.limit.RedisCharacterTalkDailyLimiter;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    @GrpcClient("ai")
    private AiServiceGrpc.AiServiceBlockingStub aiStub;

    private final ItemGatewayService itemGatewayService;
    private final ObjectMapper objectMapper;
    private final CharacterTalkAiProperties characterTalkAiProperties;
    private final CharacterTalkContextProperties characterTalkContextProperties;
    private final CharacterTalkSseProperties characterTalkSseProperties;
    private final RedisCharacterTalkDailyLimiter characterTalkDailyLimiter;

    public CharacterTypesResponse getCharacterTypes() {
        GetCharacterTypesResponse response = characterStub.getCharacterTypes(
                GetCharacterTypesRequest.newBuilder().build()
        );

        List<CharacterTypesResponse.CharacterTypeItem> items = response.getItemsList()
                .stream()
                .map(item -> CharacterTypesResponse.CharacterTypeItem.builder()
                        .id(item.getId())
                        .code(item.getCode())
                        .name(item.getName())
                        .summary(item.getSummary())
                        .sampleLine(item.getSampleLine())
                        .sortOrder(item.getSortOrder())
                        .build())
                .toList();

        return CharacterTypesResponse.builder()
                .items(items)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse getCharacterAssets(Long characterTypeId) {
        GetCharacterAssetsResponse response = characterStub.getCharacterAssets(
                GetCharacterAssetsRequest.newBuilder()
                        .setCharacterTypeId(characterTypeId)
                        .build()
        );

        List<p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.AssetItem> items = response.getItemsList()
                .stream()
                .map(item -> p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.AssetItem.builder()
                        .assetType(item.getAssetType())
                        .assetUrl(item.getAssetUrl())
                        .build())
                .toList();

        return p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.builder()
                .characterTypeId(response.getCharacterTypeId())
                .items(items)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.UserCharacterResponse createCharacter(
            Long userId, p5laris.gateway.domain.character.api.dto.CreateCharacterRequest request) {
        CreateCharacterResponse response = characterStub.createCharacter(
                CreateCharacterRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterTypeId(request.characterTypeId())
                        .setName(request.name())
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.UserCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .active(response.getActive())
                .states(p5laris.gateway.domain.character.api.dto.UserCharacterResponse.States.builder()
                        .hunger(response.getStates().getHunger())
                        .energy(response.getStates().getEnergy())
                        .affection(response.getStates().getAffection())
                        .build())
                .growth(toGrowth(response.getGrowth()))
                .createdAt(java.time.Instant.parse(response.getCreatedAt()))
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.MyCharacterResponse getMyCharacter(Long userId) {
        GetMyCharacterResponse response = characterStub.getMyCharacter(
                GetMyCharacterRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );

        p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin skin = null;
        if (response.getEquippedSkinId() > 0) {
            skin = p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin.builder()
                    .itemId(response.getEquippedSkinId())
                    .name(resolveSkinName(userId, response.getEquippedSkinId()))
                    .build();
        }

        return p5laris.gateway.domain.character.api.dto.MyCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .currentAssetUrl(response.getCurrentAssetUrl())
                .assetUrls(response.getAssetUrlsMap())
                .active(response.getActive())
                .states(p5laris.gateway.domain.character.api.dto.MyCharacterResponse.States.builder()
                        .hunger(response.getStates().getHunger())
                        .energy(response.getStates().getEnergy())
                        .affection(response.getStates().getAffection())
                        .build())
                .growth(toGrowth(response.getGrowth()))
                .equippedSkin(skin)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse updateCharacterName(
            Long characterId, Long userId, p5laris.gateway.domain.character.api.dto.UpdateCharacterNameRequest request) {
        UpdateCharacterNameResponse response = characterStub.updateCharacterName(
                UpdateCharacterNameRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .setName(request.name())
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .updatedAt(java.time.Instant.parse(response.getUpdatedAt()))
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CharacterStatusResponse getCharacterStatus(Long characterId, Long userId) {
        GetCharacterStatusResponse response = characterStub.getCharacterStatus(
                GetCharacterStatusRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.builder()
                .characterId(response.getCharacterId())
                .states(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.States.builder()
                        .hunger(toStateDetail(response.getHunger()))
                        .energy(toStateDetail(response.getEnergy()))
                        .affection(toStateDetail(response.getAffection()))
                        .build())
                .growth(toGrowth(response.getGrowth()))
                .build();
    }

    private p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse toGrowth(CharacterGrowth growth) {
        return p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse.builder()
                .level(growth.getLevel())
                .exp(growth.getExp())
                .currentLevelExp(growth.getCurrentLevelExp())
                .nextLevelExp(growth.getNextLevelExp())
                .expToNextLevel(growth.getExpToNextLevel())
                .progressPercent(growth.getProgressPercent())
                .growthStage(growth.getGrowthStage())
                .growthStageLabel(growth.getGrowthStageLabel())
                .maxLevel(growth.getMaxLevel())
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CareActionResponse performCareAction(
            Long characterId, Long userId, String idempotencyKey,
            p5laris.gateway.domain.character.api.dto.CareActionRequest request) {
        PerformCareActionResponse response = characterStub.performCareAction(
                PerformCareActionRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .setActionType(request.actionType())
                        .setItemId(request.itemId() != null ? request.itemId() : 0L)
                        .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                        .build()
        );

        Long consumedItemId = response.getConsumedItemId() > 0 ? response.getConsumedItemId() : null;

        return p5laris.gateway.domain.character.api.dto.CareActionResponse.builder()
                .careLogId(response.getCareLogId())
                .characterId(response.getCharacterId())
                .actionType(response.getActionType())
                .consumed(p5laris.gateway.domain.character.api.dto.CareActionResponse.Consumed.builder()
                        .itemId(consumedItemId)
                        .quantity(response.getConsumedQuantity())
                        .build())
                .beforeStates(p5laris.gateway.domain.character.api.dto.CareActionResponse.States.builder()
                        .hunger(response.getBeforeStates().getHunger())
                        .energy(response.getBeforeStates().getEnergy())
                        .affection(response.getBeforeStates().getAffection())
                        .build())
                .afterStates(p5laris.gateway.domain.character.api.dto.CareActionResponse.States.builder()
                        .hunger(response.getAfterStates().getHunger())
                        .energy(response.getAfterStates().getEnergy())
                        .affection(response.getAfterStates().getAffection())
                        .build())
                .beforeGrowth(toGrowth(response.getBeforeGrowth()))
                .afterGrowth(toGrowth(response.getAfterGrowth()))
                .expGained(response.getExpGained())
                .levelUp(response.getLevelUp())
                .characterMessage(response.getCharacterMessage())
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CharacterInteractionResponse interactWithCharacter(
            Long characterId,
            Long userId,
            p5laris.gateway.domain.character.api.dto.CharacterInteractionRequest request) {
        InteractWithCharacterResponse response = characterStub.interactWithCharacter(
                InteractWithCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .setInteractionType(request != null && request.interactionType() != null
                                ? request.interactionType()
                                : "TAP")
                        .build()
        );

        p5laris.gateway.domain.character.api.dto.CharacterInteractionResponse.Memory memory = null;
        if (response.hasMemory()) {
            memory = p5laris.gateway.domain.character.api.dto.CharacterInteractionResponse.Memory.builder()
                    .memoryKey(response.getMemory().getMemoryKey())
                    .title(response.getMemory().getTitle())
                    .storyText(response.getMemory().getStoryText())
                    .build();
        }

        return p5laris.gateway.domain.character.api.dto.CharacterInteractionResponse.builder()
                .characterId(response.getCharacterId())
                .characterTypeCode(response.getCharacterTypeCode())
                .level(response.getLevel())
                .fragmentType(response.getFragmentType())
                .triggerType(response.getTriggerType())
                .message(response.getMessage())
                .interpretation(response.getInterpretation())
                .memoryUnlocked(response.getMemoryUnlocked())
                .alreadyUnlocked(response.getAlreadyUnlocked())
                .memory(memory)
                .build();
    }

    public SseEmitter streamCharacterTalk(Long characterId, Long userId, CharacterTalkStreamRequest request) {
        String requestId = UUID.randomUUID().toString();
        GetCharacterTalkContextResponse context = characterStub.getCharacterTalkContext(
                GetCharacterTalkContextRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterId(characterId)
                        .setMemoryLimit(characterTalkContextProperties.normalizedMemoryLimit())
                        .build()
        );

        SseEmitter emitter = new SseEmitter(characterTalkSseProperties.normalizedTimeoutMs());
        CompletableFuture.runAsync(() -> streamTalkResponse(emitter, userId, request, requestId, context));
        return emitter;
    }

    public CharacterTalkMessagesResponse getCharacterTalkMessages(Long characterId, Long userId, LocalDate date) {
        GetCharacterTalkMessagesResponse response = aiStub
                .withDeadlineAfter(characterTalkAiProperties.normalizedDeadlineMs(), TimeUnit.MILLISECONDS)
                .getCharacterTalkMessages(GetCharacterTalkMessagesRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterId(characterId)
                        .setDate(date != null ? date.toString() : "")
                        .build());

        return new CharacterTalkMessagesResponse(
                response.getCharacterId(),
                response.getDate(),
                response.getLatestSessionId(),
                response.getMessagesList().stream()
                        .map(item -> new CharacterTalkMessagesResponse.MessageItem(
                                item.getRole(),
                                item.getContent(),
                                item.getSequence(),
                                item.getRequestId(),
                                item.getFallbackUsed(),
                                item.getCreatedAt(),
                                item.getSessionId()
                        ))
                        .toList()
        );
    }

    public CharacterTalkDiariesResponse getCharacterTalkDiaries(
            Long characterId,
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        GetCharacterTalkDiariesResponse response = aiStub
                .withDeadlineAfter(characterTalkAiProperties.normalizedDeadlineMs(), TimeUnit.MILLISECONDS)
                .getCharacterTalkDiaries(GetCharacterTalkDiariesRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterId(characterId)
                        .setFromDate(fromDate != null ? fromDate.toString() : "")
                        .setToDate(toDate != null ? toDate.toString() : "")
                        .build());

        return new CharacterTalkDiariesResponse(
                response.getCharacterId(),
                response.getFromDate(),
                response.getToDate(),
                response.getItemsList().stream()
                        .map(item -> new CharacterTalkDiariesResponse.DiaryItem(
                                item.getDate(),
                                item.getSummary(),
                                item.getSourceSessionId(),
                                item.getCreatedAt()
                        ))
                        .toList()
        );
    }

    private void streamTalkResponse(
            SseEmitter emitter,
            Long userId,
            CharacterTalkStreamRequest request,
            String requestId,
            GetCharacterTalkContextResponse context
    ) {
        TalkStreamState streamState = new TalkStreamState();
        CharacterTalkLimitResult limitResult = null;
        try {
            limitResult = characterTalkDailyLimiter.acquire(userId);
            if (!limitResult.available()) {
                sendEvent(emitter, "meta", buildTalkMeta(requestId, context, limitResult));
                streamState.metaSent = true;
                sendEvent(emitter, "delta", Map.of("text", limitFallbackTalk(
                        context.getCharacterTypeCode(),
                        limitResult.talkStatus()
                )));
                streamState.deltaSent = true;
                sendEvent(emitter, "done", buildTalkDone(requestId, true, null, limitResult));
                streamState.doneSent = true;
                emitter.complete();
                return;
            }

            Iterator<StreamCharacterTalkResponse> responses = streamCharacterTalk(userId, request, requestId, context);
            while (responses.hasNext()) {
                forwardAiTalkEvent(emitter, responses.next(), streamState, limitResult, context);
            }
            if (!streamState.doneSent) {
                sendEvent(emitter, "done", buildTalkDone(
                        requestId,
                        false,
                        AiErrorType.AI_ERROR_TYPE_UNSPECIFIED,
                        limitResult
                ));
            }
            emitter.complete();
        } catch (Exception e) {
            Status status = Status.fromThrowable(e);
            log.warn("별친구 대화 SSE 처리 중 fallback을 사용합니다. requestId={}, characterId={}, grpcStatus={}, 예외클래스={}",
                    requestId, context.getCharacterId(), status.getCode(), e.getClass().getSimpleName());
            try {
                if (!streamState.metaSent) {
                    sendEvent(emitter, "meta", buildTalkMeta(requestId, context, limitResult));
                    streamState.metaSent = true;
                }
                if (!streamState.deltaSent) {
                    sendEvent(emitter, "delta", Map.of("text", fallbackTalk(context.getCharacterTypeCode())));
                }
                sendEvent(emitter, "done", buildTalkDone(
                        requestId,
                        true,
                        AiErrorType.AI_ERROR_TYPE_PROVIDER_ERROR,
                        limitResult
                ));
                emitter.complete();
            } catch (Exception fallbackException) {
                log.error("별친구 대화 SSE fallback 전송도 실패했습니다. requestId={}, characterId={}",
                        requestId, context.getCharacterId(), fallbackException);
                emitter.completeWithError(fallbackException);
            }
        }
    }

    private Iterator<StreamCharacterTalkResponse> streamCharacterTalk(
            Long userId,
            CharacterTalkStreamRequest request,
            String requestId,
            GetCharacterTalkContextResponse context
    ) {
        return aiStub.withDeadlineAfter(characterTalkAiProperties.normalizedDeadlineMs(), TimeUnit.MILLISECONDS)
                .streamCharacterTalk(
                StreamCharacterTalkRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterId(context.getCharacterId())
                        .setCharacterType(context.getCharacterTypeCode())
                        .setCharacterName(context.getCharacterName())
                        .setUserMessage(request != null && request.message() != null ? request.message() : "")
                        .setInteractionType(request != null && request.interactionType() != null
                                ? request.interactionType()
                                : "TAP")
                        .setSessionId(request != null && request.sessionId() != null ? request.sessionId() : "")
                        .setCharacterContextJson(toCharacterContextJson(context))
                        .setRequestId(requestId)
                        .build()
                );
    }

    private void forwardAiTalkEvent(
            SseEmitter emitter,
            StreamCharacterTalkResponse response,
            TalkStreamState streamState,
            CharacterTalkLimitResult limitResult,
            GetCharacterTalkContextResponse context
    ) throws IOException {
        CharacterTalkStreamEventType eventType = response.getEventType();
        if (eventType == CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_META) {
            sendEvent(emitter, "meta", buildTalkMeta(response.getRequestId(), context, limitResult, response));
            streamState.metaSent = true;
            return;
        }

        if (eventType == CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_DELTA) {
            if (!response.getText().isBlank()) {
                if (!streamState.metaSent) {
                    sendEvent(emitter, "meta", buildTalkMeta(response.getRequestId(), context, limitResult));
                    streamState.metaSent = true;
                }
                sendEvent(emitter, "delta", Map.of("text", response.getText()));
                streamState.deltaSent = true;
            }
            return;
        }

        if (eventType == CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_DONE) {
            sendEvent(emitter, "done", buildTalkDone(
                    response,
                    limitResult
            ));
            streamState.doneSent = true;
            return;
        }

        if (eventType == CharacterTalkStreamEventType.CHARACTER_TALK_STREAM_EVENT_TYPE_ERROR) {
            sendEvent(emitter, "done", buildTalkDone(
                    response.getRequestId(),
                    true,
                    response.getErrorType(),
                    limitResult
            ));
            streamState.doneSent = true;
        }
    }

    private Map<String, Object> buildTalkMeta(
            String requestId,
            GetCharacterTalkContextResponse context,
            CharacterTalkLimitResult limitResult
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", requestId);
        meta.put("characterId", context.getCharacterId());
        meta.put("characterTypeCode", context.getCharacterTypeCode());
        meta.put("level", context.getGrowth().getLevel());
        meta.put("growth", toGrowthContext(context.getGrowth()));
        meta.put("story", toStoryProgressContext(context));
        meta.putAll(toTalkLimitContext(limitResult));
        meta.put("sentAt", Instant.now().toString());
        return meta;
    }

    private Map<String, Object> buildTalkMeta(
            String requestId,
            GetCharacterTalkContextResponse context,
            CharacterTalkLimitResult limitResult,
            StreamCharacterTalkResponse aiMeta
    ) {
        Map<String, Object> meta = buildTalkMeta(requestId, context, limitResult);
        meta.put("sessionId", aiMeta.getSessionId());
        meta.put("newSession", aiMeta.getNewSession());
        meta.put("expiresAt", aiMeta.getExpiresAt());
        meta.put("historyWindowTurns", aiMeta.getHistoryWindowTurns());
        meta.put("memorySearchTopK", aiMeta.getMemorySearchTopK());
        meta.put("memoryHitCount", aiMeta.getMemoryHitCount());
        return meta;
    }

    private Map<String, Object> buildTalkDone(
            String requestId,
            boolean fallbackUsed,
            AiErrorType errorType,
            CharacterTalkLimitResult limitResult
    ) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("requestId", requestId);
        done.put("fallbackUsed", fallbackUsed);
        done.putAll(toTalkLimitContext(limitResult));
        if (errorType != null && errorType != AiErrorType.AI_ERROR_TYPE_UNSPECIFIED) {
            done.put("errorType", errorType.name());
        }
        return done;
    }

    private Map<String, Object> buildTalkDone(
            StreamCharacterTalkResponse response,
            CharacterTalkLimitResult limitResult
    ) {
        Map<String, Object> done = buildTalkDone(
                response.getRequestId(),
                response.getFallbackUsed(),
                response.getErrorType(),
                limitResult
        );
        if (!response.getSessionId().isBlank()) {
            done.put("sessionId", response.getSessionId());
        }
        if (response.hasActualPromptTokens()) {
            done.put("actualPromptTokens", response.getActualPromptTokens());
        }
        if (response.hasActualCompletionTokens()) {
            done.put("actualCompletionTokens", response.getActualCompletionTokens());
        }
        if (response.hasActualTotalTokens()) {
            done.put("actualTotalTokens", response.getActualTotalTokens());
        }
        if (response.getMemoryHitCount() > 0) {
            done.put("memoryHitCount", response.getMemoryHitCount());
        }
        return done;
    }

    private Map<String, Object> toTalkLimitContext(CharacterTalkLimitResult limitResult) {
        Map<String, Object> body = new LinkedHashMap<>();
        CharacterTalkLimitStatus talkStatus = limitResult != null
                ? limitResult.talkStatus()
                : CharacterTalkLimitStatus.AVAILABLE;
        body.put("talkStatus", talkStatus.name());
        body.put("dailyLimit", limitResult != null ? limitResult.dailyLimit() : 0);
        body.put("remainingCount", limitResult != null ? limitResult.remainingCount() : null);
        body.put("limitExceeded", limitResult != null && limitResult.limitExceeded());
        body.put("resetAt", limitResult != null ? limitResult.resetAt() : "");
        return body;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(data)));
    }

    private String toCharacterContextJson(GetCharacterTalkContextResponse context) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("characterId", context.getCharacterId());
            body.put("characterTypeCode", context.getCharacterTypeCode());
            body.put("characterName", context.getCharacterName());
            body.put("states", Map.of(
                    "hunger", toStateContext(context.getHunger()),
                    "energy", toStateContext(context.getEnergy()),
                    "affection", toStateContext(context.getAffection())
            ));
            body.put("growth", toGrowthContext(context.getGrowth()));
            body.put("story", toStoryProgressContext(context));
            body.put("memories", context.getMemoriesList().stream()
                    .map(this::toMemoryContext)
                    .toList());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("별친구 대화 context JSON 변환 실패. characterId={}, 예외클래스={}",
                    context.getCharacterId(), e.getClass().getSimpleName());
            return "{}";
        }
    }

    private Map<String, Object> toStateContext(CharacterStateDetail state) {
        return Map.of(
                "value", state.getValue(),
                "label", state.getLabel(),
                "grade", state.getGrade()
        );
    }

    private Map<String, Object> toGrowthContext(CharacterGrowth growth) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", growth.getLevel());
        body.put("exp", growth.getExp());
        body.put("currentLevelExp", growth.getCurrentLevelExp());
        body.put("nextLevelExp", growth.getNextLevelExp());
        body.put("expToNextLevel", growth.getExpToNextLevel());
        body.put("progressPercent", growth.getProgressPercent());
        body.put("growthStage", growth.getGrowthStage());
        body.put("growthStageLabel", growth.getGrowthStageLabel());
        body.put("maxLevel", growth.getMaxLevel());
        return body;
    }

    private Map<String, Object> toStoryProgressContext(GetCharacterTalkContextResponse context) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!context.hasStoryProgress()) {
            body.put("unlockedMemoryCount", 0);
            body.put("totalMemoryCount", 0);
            body.put("allUnlocked", false);
            body.put("nextMemoryHint", null);
            return body;
        }

        CharacterStoryProgress progress = context.getStoryProgress();
        body.put("unlockedMemoryCount", progress.getUnlockedMemoryCount());
        body.put("totalMemoryCount", progress.getTotalMemoryCount());
        body.put("allUnlocked", progress.getAllUnlocked());
        if (progress.hasNextMemoryHint() && !progress.getNextMemoryHint().getHintMessage().isBlank()) {
            body.put("nextMemoryHint", Map.of(
                    "requiredLevel", progress.getNextMemoryHint().getRequiredLevel(),
                    "hintMessage", progress.getNextMemoryHint().getHintMessage()
            ));
        } else {
            body.put("nextMemoryHint", null);
        }
        return body;
    }

    private Map<String, Object> toMemoryContext(CharacterStoryMemory memory) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("memoryKey", memory.getMemoryKey());
        body.put("title", memory.getTitle());
        body.put("storyText", memory.getStoryText());
        body.put("fragmentType", memory.getFragmentType());
        body.put("unlockedLevel", memory.getUnlockedLevel());
        body.put("unlockedAt", memory.getUnlockedAt());
        return body;
    }

    private String fallbackTalk(String characterTypeCode) {
        return switch (normalizeCharacterType(characterTypeCode)) {
            case "MUMU" -> "무... 무무. (해석: 무무가 지금은 잠깐 천천히 숨을 고르자고 하는 것 같아요.)";
            case "NOVA" -> "지금은 대답이 조금 늦어졌어. 그래도 작은 숨 하나부터 다시 좌표를 잡아보자.";
            case "JJORY" -> "통신이 잠깐 삐끗함. 그래도 작전은 유지됨. 잠깐 숨 고르고 다시 가보자.";
            default -> "별친구가 지금 곁에서 천천히 들어주고 있어요. 아주 작게 숨부터 골라도 괜찮아요.";
        };
    }

    private String limitFallbackTalk(String characterTypeCode, CharacterTalkLimitStatus talkStatus) {
        if (talkStatus == CharacterTalkLimitStatus.LIMIT_EXCEEDED) {
            return switch (normalizeCharacterType(characterTypeCode)) {
                case "MUMU" -> "무... 무무. (해석: 무무가 오늘의 별빛 대화는 여기까지 아껴두고, 내일 다시 이야기하자고 하는 것 같아요.)";
                case "NOVA" -> "오늘의 교신은 여기까지야. 내일 다시 별의 좌표를 이어가자.";
                case "JJORY" -> "오늘 작전 회의는 여기까지! 내일 다시 지도 펴고 얘기하자.";
                default -> "오늘 별친구와 나눌 수 있는 대화는 여기까지예요. 내일 다시 이야기해요.";
            };
        }

        return switch (normalizeCharacterType(characterTypeCode)) {
            case "MUMU" -> "무... 무무. (해석: 무무가 별빛 연결이 잠깐 흐려졌지만, 조금 뒤에 다시 이야기하자고 하는 것 같아요.)";
            case "NOVA" -> "별빛 회선이 잠깐 흔들리고 있어. 조금 뒤에 다시 말을 걸어줘.";
            case "JJORY" -> "작전 통신이 잠깐 삐끗했음! 조금 있다가 다시 연결해보자.";
            default -> "별친구와의 연결이 잠깐 흔들리고 있어요. 조금 뒤에 다시 이야기해요.";
        };
    }

    private String normalizeCharacterType(String characterTypeCode) {
        if (characterTypeCode == null || characterTypeCode.isBlank()) {
            return "";
        }
        return characterTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    private static class TalkStreamState {
        private boolean metaSent;
        private boolean deltaSent;
        private boolean doneSent;
    }

    public p5laris.gateway.domain.character.api.dto.EquipSkinResponse equipSkin(
            Long characterId,
            Long userId,
            p5laris.gateway.domain.character.api.dto.EquipSkinRequest request) {
        EquipSkinRequest.Builder grpcRequest = EquipSkinRequest.newBuilder()
                .setCharacterId(characterId)
                .setUserId(userId);

        if (request.itemId() != null) {
            grpcRequest.setItemId(request.itemId());
        }

        EquipSkinResponse response = characterStub.equipSkin(grpcRequest.build());

        p5laris.gateway.domain.character.api.dto.EquipSkinResponse.EquippedSkin equippedSkin = null;
        if (response.getEquippedSkinId() > 0) {
            equippedSkin = p5laris.gateway.domain.character.api.dto.EquipSkinResponse.EquippedSkin.builder()
                    .itemId(response.getEquippedSkinId())
                    .name(resolveSkinName(userId, response.getEquippedSkinId()))
                    .build();
        }

        return p5laris.gateway.domain.character.api.dto.EquipSkinResponse.builder()
                .characterId(response.getCharacterId())
                .equippedSkin(equippedSkin)
                .updatedAt(java.time.Instant.parse(response.getUpdatedAt()))
                .build();
    }

    private p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail toStateDetail(
            com.p5laris.proto.character.v1.CharacterStateDetail state) {
        return p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail.builder()
                .value(state.getValue())
                .label(state.getLabel())
                .grade(state.getGrade())
                .build();
    }

    private String resolveSkinName(Long userId, Long itemId) {
        try {
            return itemGatewayService.getUserItems(userId, "SKIN", "", 100)
                    .getItemsList()
                    .stream()
                    .filter(item -> item.getItemId() == itemId)
                    .findFirst()
                    .map(UserItem::getName)
                    .orElse("Skin " + itemId);
        } catch (Exception e) {
            return "Skin " + itemId;
        }
    }
}
