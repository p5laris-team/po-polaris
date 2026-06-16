package p5laris.ai.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.CharacterTalkGenerator;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.policy.CharacterTalkValidationPolicy;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;

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

    private static final String[] MUMU_POSITIVE_FALLBACK_REPLIES = {
            "무! 무무! (해석: 헤헤, 그 말 좋다. 나 지금 조금 신났어.)",
            "무무! 무우! (해석: 좋아, 이건 같이 기뻐해야 하는 일이지. 더 말해줘.)",
            "무! 무무무! (해석: 칭찬 들으니까 나도 힘이 난다. 너도 오늘 꽤 멋졌어.)"
    };
    private static final String[] MUMU_SOFT_FALLBACK_REPLIES = {
            "무무 ㅠㅠ... (해석: 오늘 많이 버거웠구나. 나 지금 네 편에서 듣고 있어.)",
            "무우...? 무무 ㅠㅠ (해석: 그건 마음이 좀 다쳤겠다. 천천히 말해줘도 돼.)",
            "무... ㅠ 무무. (해석: 억지로 괜찮은 척 안 해도 돼. 나 여기 있어.)"
    };
    private static final String[] MUMU_NEUTRAL_FALLBACK_REPLIES = {
            "무우...? 무무. (해석: 응, 나 듣고 있어. 방금 말 조금 더 들려줘.)",
            "무...? 무! (해석: 나 불렀어? 여기 있어. 오늘은 어떤 얘기부터 할까?)",
            "무무. 무우...? (해석: 좋아, 천천히 이어가자. 지금 제일 먼저 떠오른 말이 뭐야?)"
    };

    private final CharacterTalkGenerator characterTalkGenerator;
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
        StreamingReplyGuard guard = new StreamingReplyGuard(command, emitter);
        AiTokenUsage tokenUsage = characterTalkGenerator.stream(command, guard::accept);
        guard.finish();
        return tokenUsage;
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
            case "MUMU" -> mumuFallbackReply(command);
            case "NOVA" -> "지금 말이 조금 흐려져도 괜찮아. 내가 여기서 천천히 같이 짚어볼게.";
            case "JJORY" -> "지금은 급히 작전 짤 필요 없음. 일단 여기 앉아서 네 말부터 들어볼게.";
            default -> "별친구가 여기서 천천히 들어줄게요. 지금 말부터 편하게 이어가도 괜찮아요.";
        };
    }

    private String mumuFallbackReply(CharacterTalkGenerationCommand command) {
        String message = normalizeForKeyword(command.userMessage());
        if (containsAny(message, "좋", "기쁘", "신나", "행복", "성공", "합격", "해냈", "잘했", "칭찬", "최고", "예쁘", "사랑", "고마", "대박")) {
            return pickMumuFallback(command, MUMU_POSITIVE_FALLBACK_REPLIES);
        }
        if (containsAny(message, "힘들", "피곤", "지쳤", "우울", "슬프", "외롭", "짜증", "화나", "싫", "불안", "망했", "아파", "눈물", "울", "빡")) {
            return pickMumuFallback(command, MUMU_SOFT_FALLBACK_REPLIES);
        }
        return pickMumuFallback(command, MUMU_NEUTRAL_FALLBACK_REPLIES);
    }

    private String pickMumuFallback(CharacterTalkGenerationCommand command, String[] candidates) {
        String seed = safeText(command.userMessage()) + "|" + safeText(command.requestId());
        int index = Math.floorMod(seed.hashCode(), candidates.length);
        return candidates[index];
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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

    private String displayCharacterName(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return "무무";
        }
        return characterName.trim();
    }

    private String subjectParticle(String value) {
        if (value.isBlank()) {
            return "가";
        }
        char last = value.charAt(value.length() - 1);
        if (last < '가' || last > '힣') {
            return "가";
        }
        return (last - '가') % 28 == 0 ? "가" : "이";
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
        private int mumuEmittedLength;
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
                emitSafeMumuUtterancePrefix(buffered);
                return;
            }

            validateMumuUtterancePrefix(buffered.substring(0, interpretationStart).trim());
            mumuReleased = true;
            emitMumuDelta(buffered.substring(mumuEmittedLength));
            mumuEmittedLength = buffered.length();
            mumuBuffer.setLength(0);
        }

        private void emitSafeMumuUtterancePrefix(String buffered) {
            int safeBoundary = buffered.length() - trailingInterpretationMarkerPrefixLength(buffered);
            if (safeBoundary <= mumuEmittedLength) {
                return;
            }

            String safePrefix = buffered.substring(0, safeBoundary);
            validateMumuUtterancePrefix(safePrefix.trim());
            emitMumuDelta(buffered.substring(mumuEmittedLength, safeBoundary));
            mumuEmittedLength = safeBoundary;
        }

        private int trailingInterpretationMarkerPrefixLength(String value) {
            String marker = "(해석:";
            int maxLength = Math.min(marker.length() - 1, value.length());
            for (int length = maxLength; length > 0; length--) {
                if (marker.startsWith(value.substring(value.length() - length))) {
                    return length;
                }
            }
            return 0;
        }

        private void emitMumuDelta(String text) {
            if (text != null && !text.isBlank()) {
                emitter.emitDelta(useMumuCharacterName(text, command.characterName()));
            }
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
                    || character == 'ㅠ'
                    || Character.isWhitespace(character);
        }
    }

    private String useMumuCharacterName(String value, String characterName) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String name = displayCharacterName(characterName);
        return value.replace("무무가", name + subjectParticle(name));
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
