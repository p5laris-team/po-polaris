package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import reactor.core.publisher.Flux;

/**
 * Spring AI ChatClient를 감싸는 adapter다.
 *
 * ChatClient가 없다는 것은 spring.ai.model.chat 설정이나 API key 설정이 맞지 않는다는 뜻이므로
 * 외부 호출을 시도하지 않고 fallback 경로로 내려보낸다.
 */
@Component
@RequiredArgsConstructor
public class SpringAiChatClient implements AiChatClient {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        return requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content();
    }

    @Override
    public Flux<String> streamPlainText(String systemPrompt, String userPrompt) {
        return requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(GoogleGenAiChatOptions.builder()
                        .responseMimeType("text/plain"))
                .stream()
                .content();
    }

    @Override
    public Flux<String> streamPlainTextWithTools(String systemPrompt, String userPrompt, Object... tools) {
        ChatClientRequestSpec requestSpec = requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(GoogleGenAiChatOptions.builder()
                        .responseMimeType("text/plain"));

        if (tools != null && tools.length > 0) {
            requestSpec.tools(tools);
        }

        return requestSpec.stream().content();
    }

    private ChatClient.Builder requireBuilder() {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new FallbackRequiredException(
                    AiErrorType.PROVIDER_ERROR,
                    "Spring AI ChatClient가 구성되지 않았습니다."
            );
        }
        return builder;
    }
}
