package p5laris.ai.domain.application.prompt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbPromptTemplateService implements PromptTemplateService {

    private static final String SYSTEM_MARKER = "[[SYSTEM]]";
    private static final String USER_MARKER = "[[USER]]";

    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptRenderer promptRenderer;

    @Override
    public RenderedPrompt render(
            PromptCategory category,
            Map<String, ?> variables,
            RenderedPrompt fallback
    ) {
        return promptTemplateRepository
                .findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(category)
                .flatMap(template -> renderTemplate(category, template, variables))
                .orElse(fallback);
    }

    private Optional<RenderedPrompt> renderTemplate(
            PromptCategory category,
            PromptTemplate promptTemplate,
            Map<String, ?> variables
    ) {
        return PromptSections.parse(promptTemplate.getTemplate())
                .map(sections -> new RenderedPrompt(
                        promptRenderer.render(sections.systemPrompt(), variables),
                        promptRenderer.render(sections.userPrompt(), variables)
                ))
                .or(() -> {
                    log.warn("활성 프롬프트 템플릿 형식이 올바르지 않아 기본 프롬프트를 사용합니다. category={}, templateId={}",
                            category, promptTemplate.getId());
                    return Optional.empty();
                });
    }

    private record PromptSections(
            String systemPrompt,
            String userPrompt
    ) {

        private static Optional<PromptSections> parse(String template) {
            if (template == null || template.isBlank()) {
                return Optional.empty();
            }

            int systemIndex = template.indexOf(SYSTEM_MARKER);
            int userIndex = template.indexOf(USER_MARKER);
            if (systemIndex < 0 || userIndex < 0 || systemIndex >= userIndex) {
                return Optional.empty();
            }

            String systemPrompt = template
                    .substring(systemIndex + SYSTEM_MARKER.length(), userIndex)
                    .trim();
            String userPrompt = template
                    .substring(userIndex + USER_MARKER.length())
                    .trim();

            if (systemPrompt.isBlank() || userPrompt.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PromptSections(systemPrompt, userPrompt));
        }
    }
}
