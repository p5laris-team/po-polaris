package p5laris.ai.domain.domain.policy;

import org.springframework.stereotype.Component;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.CharacterToneType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import java.util.Locale;

/**
 * 요청으로 들어온 characterType 문자열을 내부 CharacterToneType으로 변환하는 정책이다.
 *
 * 알 수 없는 캐릭터 타입은 잘못된 입력이지만, 사용자 흐름을 끊지 않기 위해 fallback 대상 예외로 처리한다.
 */
@Component
public class CharacterTonePolicy {

    // proto에서는 문자열로 들어오므로 대소문자와 앞뒤 공백을 정리한 뒤 enum으로 변환한다.
    public CharacterToneType resolve(String characterType) {
        if (characterType == null || characterType.isBlank()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "캐릭터 타입이 비어 있습니다.");
        }

        try {
            return CharacterToneType.valueOf(characterType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "지원하지 않는 캐릭터 타입입니다.");
        }
    }
}
