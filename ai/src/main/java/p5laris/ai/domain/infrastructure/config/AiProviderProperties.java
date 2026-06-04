package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import p5laris.ai.domain.domain.enums.AiProviderType;

/**
 * AI provider 설정값을 application.yaml/env에서 읽어오는 클래스다.
 *
 * 실제 실행 값은 application.yaml이 참조하는 env에서 주입된다.
 * enabled=false면 rule-based generator만 사용하고, enabled=true + type=gemini면 외부 Gemini provider를 호출한다.
 */
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {

    private boolean enabled;
    private String type;
    private String model;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public AiProviderType providerType() {
        return AiProviderType.from(type);
    }

    public boolean isExternalEnabled() {
        return enabled && providerType().isExternal();
    }

    // model 값이 비어 있으면 provider별 안전 기본 모델명으로 보정한다. 운영에서는 env에 명시하는 것을 원칙으로 한다.
    public String resolvedModel() {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }

        return switch (providerType()) {
            case GEMINI -> "gemini-2.5-flash";
            case OPENAI -> "gpt-5-mini";
            case LOCAL, UNKNOWN -> "local-tone-v1";
        };
    }
}
