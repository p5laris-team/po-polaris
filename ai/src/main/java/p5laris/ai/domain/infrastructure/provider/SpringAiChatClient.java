package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.generator.AiChatStreamChunk;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.application.generator.AiChatResponse;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;

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
    public AiChatResponse callWithUsage(String systemPrompt, String userPrompt) {
        ChatResponse response = requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        return new AiChatResponse(extractText(response), extractTokenUsage(response));
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

    @Override
    public Flux<AiChatStreamChunk> streamPlainTextWithToolsAndUsage(
            String systemPrompt,
            String userPrompt,
            Object... tools
    ) {
        ChatClientRequestSpec requestSpec = requireBuilder().build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(GoogleGenAiChatOptions.builder()
                        .responseMimeType("text/plain"));

        if (tools != null && tools.length > 0) {
            requestSpec.tools(tools);
        }

        return requestSpec.stream()
                .chatResponse()
                .map(response -> new AiChatStreamChunk(
                        extractText(response),
                        extractTokenUsage(response)
                ));
    }

    private String extractText(ChatResponse response) {
        Object result = invoke(response, "getResult");
        Object output = invoke(result, "getOutput");
        Object text = invoke(output, "getText");
        return text instanceof String value ? value : "";
    }

    private AiTokenUsage extractTokenUsage(ChatResponse response) {
        Object metadata = invoke(response, "getMetadata");
        Object usage = invoke(metadata, "getUsage");
        if (usage == null) {
            return AiTokenUsage.empty();
        }

        return new AiTokenUsage(
                firstInteger(
                        invoke(usage, "getPromptTokens"),
                        invoke(usage, "getPromptTokenCount"),
                        invoke(usage, "promptTokens")
                ),
                firstInteger(
                        invoke(usage, "getCompletionTokens"),
                        invoke(usage, "getGenerationTokens"),
                        invoke(usage, "getCompletionTokenCount"),
                        invoke(usage, "completionTokens")
                ),
                firstInteger(
                        invoke(usage, "getTotalTokens"),
                        invoke(usage, "getTotalTokenCount"),
                        invoke(usage, "totalTokens")
                )
        );
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Integer firstInteger(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Integer integerValue = toInteger(value);
            if (integerValue != null) {
                return integerValue;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            long longValue = number.longValue();
            if (longValue >= 0 && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
        }
        return null;
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
