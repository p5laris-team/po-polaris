package p5laris.ai.domain.domain.enums;

import java.util.Locale;

/**
 * AI 문구 생성에 사용할 provider 종류를 표현하는 enum이다.
 *
 * application.yaml/env에는 문자열로 들어오지만, 서비스 코드에서는 enum으로 바꿔서 분기 실수를 줄인다.
 */
public enum AiProviderType {
    LOCAL,
    GEMINI,
    OPENAI,
    UNKNOWN;

    public static AiProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }

        try {
            return AiProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isExternal() {
        return this == GEMINI || this == OPENAI;
    }
}
