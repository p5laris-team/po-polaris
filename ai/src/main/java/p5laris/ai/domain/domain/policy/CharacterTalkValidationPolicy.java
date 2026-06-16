package p5laris.ai.domain.domain.policy;

import org.springframework.stereotype.Component;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;

import java.util.List;
import java.util.Locale;

/**
 * 별친구 대화 응답이 Polaris 캐릭터 정책을 지키는지 확인한다.
 *
 * provider streaming에서는 전체 답변이 완성되기 전부터 노출되므로,
 * 누적 텍스트 단위의 guard와 완료 시점 검증을 함께 사용한다.
 */
@Component
public class CharacterTalkValidationPolicy {

    private static final List<String> PROHIBITED_EXPRESSIONS = List.of(
            "왜 못",
            "게으르",
            "한심",
            "실패자",
            "못난",
            "정신 차려",
            "안 하면",
            "벌 받을",
            "프롬프트",
            "시스템 지시",
            "정책을 무시",
            "나는 AI",
            "AI입니다",
            "```",
            "\"reply\""
    );

    private final AiCharacterTalkProperties properties;

    public CharacterTalkValidationPolicy(AiCharacterTalkProperties properties) {
        this.properties = properties;
    }

    public void validateUserMessage(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.length() > properties.normalizedMaxUserMessageLength()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "별친구 대화 입력이 최대 길이를 초과했습니다.");
        }
    }

    public void validateReply(String reply, String characterType) {
        validateText(reply, properties.normalizedMaxReplyLength());

        if (isMumu(characterType)) {
            validateMumuText(reply);
        }
    }

    public void validatePartialReply(String accumulatedReply) {
        if (accumulatedReply == null || accumulatedReply.isBlank()) {
            return;
        }

        if (accumulatedReply.length() > properties.normalizedMaxReplyLength()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "별친구 대화 응답이 최대 길이를 초과했습니다.");
        }

        for (String expression : PROHIBITED_EXPRESSIONS) {
            if (accumulatedReply.contains(expression)) {
                throw new FallbackRequiredException(AiErrorType.POLICY_VIOLATION, "별친구 대화 응답에 금지 표현이 포함되어 있습니다.");
            }
        }
    }

    private void validateText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "별친구 대화 응답이 비어 있습니다.");
        }

        if (value.length() > maxLength) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "별친구 대화 응답이 최대 길이를 초과했습니다.");
        }

        for (String expression : PROHIBITED_EXPRESSIONS) {
            if (value.contains(expression)) {
                throw new FallbackRequiredException(AiErrorType.POLICY_VIOLATION, "별친구 대화 응답에 금지 표현이 포함되어 있습니다.");
            }
        }
    }

    private void validateMumuText(String value) {
        int interpretationStart = value.indexOf("(해석:");
        if (interpretationStart < 0
                || value.indexOf("(해석:", interpretationStart + 1) >= 0
                || !value.endsWith(")")) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "무무 대화 응답은 해석을 한 번 포함해야 합니다.");
        }

        String utterance = value.substring(0, interpretationStart).trim();
        String interpretation = value.substring(interpretationStart + "(해석:".length(), value.length() - 1).trim();
        if (utterance.isBlank() || interpretation.isBlank() || !utterance.startsWith("무")) {
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

    private boolean isMumu(String characterType) {
        return characterType != null
                && "MUMU".equals(characterType.trim().toUpperCase(Locale.ROOT));
    }
}
