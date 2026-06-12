package p5laris.ai.domain.application.prompt;

import p5laris.ai.domain.domain.enums.PromptCategory;

import java.util.Map;

public interface PromptTemplateService {

    RenderedPrompt render(
            PromptCategory category,
            Map<String, ?> variables,
            RenderedPrompt fallback
    );
}
