package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.tool.CharacterTalkToolsFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiCharacterTalkGeneratorTest {

    @Test
    @DisplayName("별친구 대화 - Tool 객체를 provider streaming 호출에 함께 전달한다")
    void stream_passesToolsToProviderCall() {
        CapturingAiChatClient chatClient = new CapturingAiChatClient("무... 무무. (해석: 무무가 같이 있어도 괜찮다고 하는 것 같아요.)");
        AiCharacterTalkProperties properties = new AiCharacterTalkProperties();
        Object toolObject = new TestTool();
        CharacterTalkToolsFactory toolsFactory = mock(CharacterTalkToolsFactory.class);
        CharacterTalkGenerationCommand command = command();
        when(toolsFactory.create(command)).thenReturn(toolObject);

        GeminiCharacterTalkGenerator generator = new GeminiCharacterTalkGenerator(
                chatClient,
                properties,
                toolsFactory
        );

        List<String> chunks = new ArrayList<>();
        generator.stream(command, chunks::add);

        assertEquals("무... 무무. (해석: 무무가 같이 있어도 괜찮다고 하는 것 같아요.)", String.join("", chunks));
        assertSame(toolObject, chatClient.tools[0]);
        assertTrue(chatClient.systemPrompt.contains("getCharacterStatus Tool과 getUnlockedCharacterMemories Tool을 먼저 호출"));
        assertTrue(chatClient.userPrompt.contains("fallbackContext JSON"));
    }

    private CharacterTalkGenerationCommand command() {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                "MUMU",
                "무무",
                "오늘 좀 피곤해",
                "TAP",
                "{\"fallback\":true}",
                "request-1"
        );
    }

    private static class CapturingAiChatClient implements AiChatClient {

        private final String content;
        private String systemPrompt;
        private String userPrompt;
        private Object[] tools;

        private CapturingAiChatClient(String content) {
            this.content = content;
        }

        @Override
        public String call(String systemPrompt, String userPrompt) {
            return content;
        }

        @Override
        public Flux<String> streamPlainTextWithTools(String systemPrompt, String userPrompt, Object... tools) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.tools = tools;
            return Flux.just(content);
        }
    }

    private static class TestTool {
    }
}
