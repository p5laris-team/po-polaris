package p5laris.ai.domain.application.generator;

import reactor.core.publisher.Flux;

/**
 * Spring AI ChatClient를 직접 application service에 노출하지 않기 위한 작은 포트다.
 *
 * 테스트에서는 이 포트를 fake로 바꿔 실제 Gemini API key 없이도 provider 동작을 검증한다.
 */
public interface AiChatClient {

    String call(String systemPrompt, String userPrompt);

    default Flux<String> stream(String systemPrompt, String userPrompt) {
        return Flux.just(call(systemPrompt, userPrompt));
    }

    default Flux<String> streamPlainText(String systemPrompt, String userPrompt) {
        return stream(systemPrompt, userPrompt);
    }

    default Flux<String> streamPlainTextWithTools(String systemPrompt, String userPrompt, Object... tools) {
        return streamPlainText(systemPrompt, userPrompt);
    }

    default Flux<AiChatStreamChunk> streamPlainTextWithToolsAndUsage(
            String systemPrompt,
            String userPrompt,
            Object... tools
    ) {
        return streamPlainTextWithTools(systemPrompt, userPrompt, tools)
                .map(AiChatStreamChunk::text);
    }
}
