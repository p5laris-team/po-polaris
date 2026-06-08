package p5laris.ai.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.domain.policy.CharacterTalkValidationPolicy;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.provider.AiProviderCircuitBreaker;
import p5laris.ai.domain.infrastructure.provider.GeminiCharacterTalkGenerator;

import java.util.Locale;

/**
 * 별친구 대화 생성 유스케이스다.
 *
 * 최근 대화는 세션 단위로 짧게 보관하고, provider 호출 직전에 rate limit/서킷 브레이커를 적용한다.
 * provider streaming이 실패해도 사용자 흐름은 끊지 않고 캐릭터별 fallback 또는 done 이벤트로 닫는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiCharacterTalkService {

    private final AiProviderProperties aiProviderProperties;
    private final AiRateLimiter aiRateLimiter;
    private final AiProviderCircuitBreaker aiProviderCircuitBreaker;
    private final GeminiCharacterTalkGenerator geminiCharacterTalkGenerator;
    private final CharacterTalkValidationPolicy characterTalkValidationPolicy;
    private final CharacterTalkHistoryService characterTalkHistoryService;

    public void streamCharacterTalk(CharacterTalkGenerationCommand command, CharacterTalkStreamEmitter emitter) {
        validateRequest(command);
        PreparedCharacterTalkContext context = null;
        CharacterTalkGenerationCommand runtimeCommand = command;
        RecordingCharacterTalkStreamEmitter recordingEmitter = new RecordingCharacterTalkStreamEmitter(emitter);

        try {
            characterTalkValidationPolicy.validateUserMessage(command.userMessage());
            context = characterTalkHistoryService.prepare(command);
            runtimeCommand = command.withConversationContext(
                    context.sessionId(),
                    context.conversationHistoryJson(),
                    context.memoryContextJson()
            );
            emitter.emitMeta(context.toMetadata(command.requestId()));
            AiTokenUsage tokenUsage = streamReply(runtimeCommand, recordingEmitter);
            characterTalkHistoryService.recordAssistantResponse(
                    context,
                    runtimeCommand,
                    recordingEmitter.text(),
                    false,
                    tokenUsage
            );
            emitter.complete(false, null, tokenUsage);
        } catch (FallbackRequiredException e) {
            log.warn("별친구 대화 생성 fallback 사용. requestId={}, characterType={}, errorType={}, reason={}",
                    command.requestId(), command.characterType(), e.getErrorType(), e.getMessage());
            completeWithFallback(runtimeCommand, recordingEmitter, e.getErrorType());
            if (context != null) {
                characterTalkHistoryService.recordAssistantResponse(
                        context,
                        runtimeCommand,
                        recordingEmitter.text(),
                        true,
                        AiTokenUsage.empty()
                );
            }
        }
    }

    private AiTokenUsage streamReply(CharacterTalkGenerationCommand command, CharacterTalkStreamEmitter emitter) {
        AiProviderType providerType = aiProviderProperties.providerType();
        if (!aiProviderProperties.isExternalEnabled()) {
            throw new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "외부 AI provider가 비활성화되어 있습니다.");
        }
        if (providerType != AiProviderType.GEMINI) {
            throw new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "지원하지 않는 별친구 대화 provider입니다.");
        }

        aiRateLimiter.checkAllowed(command.userId(), providerType, aiProviderProperties.resolvedModel());
        return aiProviderCircuitBreaker.execute(
                providerType,
                aiProviderProperties.resolvedModel(),
                command.requestId(),
                () -> {
                    StreamingReplyGuard guard = new StreamingReplyGuard(command, emitter);
                    AiTokenUsage tokenUsage = geminiCharacterTalkGenerator.stream(command, guard::accept);
                    guard.finish();
                    return tokenUsage;
                }
        );
    }

    private void validateRequest(CharacterTalkGenerationCommand command) {
        if (command == null
                || isNotPositive(command.userId())
                || isNotPositive(command.characterId())
                || isBlank(command.characterType())
                || isBlank(command.requestId())) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }

    private String fallbackReply(CharacterTalkGenerationCommand command) {
        return switch (normalize(command.characterType())) {
            case "MUMU" -> "무... 무무. (해석: 무무가 지금은 아주 작게 숨을 고르고, 곁에 같이 있어도 괜찮다고 하는 것 같아요.)";
            case "NOVA" -> "지금 마음이 조금 흐려도 괜찮아. 작은 숨 하나부터 다시 좌표를 잡아보자.";
            case "JJORY" -> "오늘은 잠깐 멈춰도 됨. 지도 접는 것도 작전임. 다시 펼치면 됨.";
            default -> "별친구가 지금 곁에서 천천히 들어주고 있어요. 오늘은 아주 작게 시작해도 괜찮아요.";
        };
    }

    private void completeWithFallback(
            CharacterTalkGenerationCommand command,
            CharacterTalkStreamEmitter emitter,
            AiErrorType errorType
    ) {
        if (!emitter.hasEmittedDelta()) {
            String fallbackReply = fallbackReply(command);
            characterTalkValidationPolicy.validateReply(fallbackReply, command.characterType());
            emitter.emitDelta(fallbackReply);
        }
        emitter.complete(true, errorType, AiTokenUsage.empty());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isNotPositive(Long value) {
        return value == null || value <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private class StreamingReplyGuard {

        private final CharacterTalkGenerationCommand command;
        private final CharacterTalkStreamEmitter emitter;
        private final StringBuilder accumulatedReply = new StringBuilder();
        private final StringBuilder mumuBuffer = new StringBuilder();
        private final boolean mumu;
        private boolean mumuReleased;

        private StreamingReplyGuard(CharacterTalkGenerationCommand command, CharacterTalkStreamEmitter emitter) {
            this.command = command;
            this.emitter = emitter;
            this.mumu = "MUMU".equals(normalize(command.characterType()));
        }

        private void accept(String chunk) {
            if (chunk == null || chunk.isBlank()) {
                return;
            }

            accumulatedReply.append(chunk);
            characterTalkValidationPolicy.validatePartialReply(accumulatedReply.toString());

            if (mumu) {
                acceptMumuChunk(chunk);
                return;
            }
            emitter.emitDelta(chunk);
        }

        private void acceptMumuChunk(String chunk) {
            if (mumuReleased) {
                emitter.emitDelta(chunk);
                return;
            }

            mumuBuffer.append(chunk);
            String buffered = mumuBuffer.toString();
            int interpretationStart = buffered.indexOf("(해석:");
            if (interpretationStart < 0) {
                if (buffered.length() > 80) {
                    throw new FallbackRequiredException(
                            AiErrorType.INVALID_OUTPUT,
                            "무무 대화 응답에 해석 라벨이 너무 늦게 등장했습니다."
                    );
                }
                return;
            }

            validateMumuUtterancePrefix(buffered.substring(0, interpretationStart).trim());
            mumuReleased = true;
            emitter.emitDelta(buffered);
            mumuBuffer.setLength(0);
        }

        private void finish() {
            if (mumu && !mumuReleased) {
                throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "무무 대화 응답에 해석 라벨이 없습니다.");
            }
            characterTalkValidationPolicy.validateReply(accumulatedReply.toString(), command.characterType());
        }

        private void validateMumuUtterancePrefix(String utterance) {
            if (utterance.isBlank() || !utterance.startsWith("무")) {
                throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "무무 대화 응답 형식이 올바르지 않습니다.");
            }
            for (int index = 0; index < utterance.length(); index++) {
                char character = utterance.charAt(index);
                if (isAllowedMumuUtteranceCharacter(character)) {
                    continue;
                }
                throw new FallbackRequiredException(
                        AiErrorType.INVALID_OUTPUT,
                        "무무 대화 응답의 해석 밖에는 무무 발화만 사용할 수 있습니다."
                );
            }
        }

        private boolean isAllowedMumuUtteranceCharacter(char character) {
            return character == '무'
                    || character == '우'
                    || character == '.'
                    || character == '?'
                    || character == '!'
                    || character == '…'
                    || Character.isWhitespace(character);
        }
    }

    private static class RecordingCharacterTalkStreamEmitter implements CharacterTalkStreamEmitter {

        private final CharacterTalkStreamEmitter delegate;
        private final StringBuilder text = new StringBuilder();

        private RecordingCharacterTalkStreamEmitter(CharacterTalkStreamEmitter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void emitMeta(p5laris.ai.domain.application.dto.CharacterTalkStreamMetadata metadata) {
            delegate.emitMeta(metadata);
        }

        @Override
        public void emitDelta(String text) {
            if (text != null) {
                this.text.append(text);
            }
            delegate.emitDelta(text);
        }

        @Override
        public void complete(boolean fallbackUsed, AiErrorType errorType, AiTokenUsage tokenUsage) {
            delegate.complete(fallbackUsed, errorType, tokenUsage);
        }

        @Override
        public boolean hasEmittedDelta() {
            return delegate.hasEmittedDelta();
        }

        private String text() {
            return text.toString();
        }
    }
}
