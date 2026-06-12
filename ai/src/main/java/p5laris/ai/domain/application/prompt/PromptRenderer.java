package p5laris.ai.domain.application.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public String render(String template, Map<String, ?> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(valueOf(variables, matcher.group(1))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String valueOf(Map<String, ?> variables, String key) {
        if (variables == null || !variables.containsKey(key)) {
            return "";
        }
        Object value = variables.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
