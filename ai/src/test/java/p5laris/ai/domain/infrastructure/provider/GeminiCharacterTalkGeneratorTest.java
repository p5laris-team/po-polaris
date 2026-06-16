package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.application.prompt.PromptTemplateService;
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

    private static final PromptTemplateService FALLBACK_PROMPTS = (category, variables, fallback) -> fallback;

    @Test
    @DisplayName("별친구 대화 - Tool 객체를 provider streaming 호출에 함께 전달한다")
    void stream_passesToolsToProviderCall() {
        CapturingAiChatClient chatClient = new CapturingAiChatClient("무무 ㅠㅠ... (해석: 나 여기 있어. 오늘 말이 조금 엉켜도 천천히 들려줘.)");
        AiCharacterTalkProperties properties = new AiCharacterTalkProperties();
        Object toolObject = new TestTool();
        CharacterTalkToolsFactory toolsFactory = mock(CharacterTalkToolsFactory.class);
        CharacterTalkGenerationCommand command = command();
        when(toolsFactory.create(command)).thenReturn(toolObject);

        GeminiCharacterTalkGenerator generator = new GeminiCharacterTalkGenerator(
                chatClient,
                properties,
                toolsFactory,
                FALLBACK_PROMPTS
        );

        List<String> chunks = new ArrayList<>();
        generator.stream(command, chunks::add);

        assertEquals("무무 ㅠㅠ... (해석: 나 여기 있어. 오늘 말이 조금 엉켜도 천천히 들려줘.)", String.join("", chunks));
        assertSame(toolObject, chatClient.tools[0]);
        assertTrue(chatClient.systemPrompt.contains("몸이나 마음의 불편"));
        assertTrue(chatClient.systemPrompt.contains("작고 구체적인 행동 1가지"));
        assertTrue(chatClient.systemPrompt.contains("추상적인 위로만으로 끝내지 않는다"));
        assertTrue(chatClient.systemPrompt.contains("편두통이면 화면 밝기"));
        assertTrue(chatClient.systemPrompt.contains("캐릭터별 말투 형식은 반드시 지키되"));
        assertTrue(chatClient.systemPrompt.contains("통역 설명문이 아니라 사용자에게 직접 건네는 말"));
        assertTrue(chatClient.userPrompt.contains("\"캐릭터 이름\"은 별친구 자신의 이름"));
        assertTrue(chatClient.systemPrompt.contains("MUMU 편두통 예시"));
        assertTrue(chatClient.userPrompt.contains("사용자 이름으로 착각하지 않는다"));
        assertTrue(chatClient.userPrompt.contains("현재 KST 시간 context"));
        assertTrue(chatClient.userPrompt.contains("timeBucket="));
        assertTrue(chatClient.userPrompt.contains("최우선으로 \"이번에 반드시 답해야 하는 최신 사용자 대화 입력\"에 답한다"));
        assertTrue(chatClient.userPrompt.contains("fallbackContext JSON"));
        assertTrue(chatClient.userPrompt.contains("conversationHistory JSON"));
        assertTrue(chatClient.userPrompt.contains("longTermMemoryContext JSON"));
        assertTrue(chatClient.userPrompt.contains("기억하고 있다"));
        assertTrue(chatClient.userPrompt.contains("휴식이나 수면 준비"));
    }

    @Test
    @DisplayName("별친구 대화 - provider prompt에 거친 표현 원문을 그대로 싣지 않는다")
    void stream_softensRoughWordsBeforeProviderPrompt() {
        CapturingAiChatClient chatClient = new CapturingAiChatClient("무무 ㅠㅠ... (해석: 오늘 진짜 많이 힘 빠졌겠다.)");
        AiCharacterTalkProperties properties = new AiCharacterTalkProperties();
        Object toolObject = new TestTool();
        CharacterTalkToolsFactory toolsFactory = mock(CharacterTalkToolsFactory.class);
        CharacterTalkGenerationCommand command = new CharacterTalkGenerationCommand(
                1L,
                1L,
                "MUMU",
                "무다리",
                "나 오늘 회사 갔다 왔는데 너무 힘들고 족같았음...",
                "CHAT",
                "{}",
                "request-1"
        );
        when(toolsFactory.create(command)).thenReturn(toolObject);

        GeminiCharacterTalkGenerator generator = new GeminiCharacterTalkGenerator(
                chatClient,
                properties,
                toolsFactory,
                FALLBACK_PROMPTS
        );

        generator.stream(command, ignored -> {
        });

        assertTrue(!chatClient.userPrompt.contains("족같"));
        assertTrue(chatClient.userPrompt.contains("정말 힘들었음"));
    }

    private CharacterTalkGenerationCommand command() {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                "MUMU",
                "무다리",
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
