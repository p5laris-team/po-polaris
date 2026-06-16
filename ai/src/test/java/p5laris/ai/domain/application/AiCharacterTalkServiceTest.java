package p5laris.ai.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.CharacterTalkStreamMetadata;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.CharacterTalkGenerator;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.policy.CharacterTalkValidationPolicy;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCharacterTalkServiceTest {

    @Test
    @DisplayName("별친구 대화 - provider 실패 시 캐릭터별 fallback을 반환")
    void streamCharacterTalk_providerFailure_returnsFallback() {
        CharacterTalkGenerator generator = mock(CharacterTalkGenerator.class);
        AiCharacterTalkService service = service(300, generator);
        CapturingEmitter emitter = new CapturingEmitter();
        when(generator.stream(any(), any()))
                .thenThrow(new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "disabled"));

        service.streamCharacterTalk(command("MUMU", "무무야 안녕"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.PROVIDER_ERROR, emitter.errorType);
        assertTrue(emitter.joinedText().startsWith("무"));
        assertTrue(emitter.joinedText().contains("(해석:"));
        verify(generator).stream(any(), any());
    }

    @Test
    @DisplayName("별친구 대화 - 사용자 입력이 너무 길면 provider 호출 없이 fallback을 반환")
    void streamCharacterTalk_tooLongMessage_returnsFallback() {
        CharacterTalkGenerator generator = mock(CharacterTalkGenerator.class);
        AiCharacterTalkService service = service(3, generator);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("NOVA", "너무 긴 메시지"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.INVALID_OUTPUT, emitter.errorType);
        assertTrue(emitter.joinedText().contains("천천히 같이 짚어볼게"));
        verify(generator, never()).stream(any(), any());
    }

    @Test
    @DisplayName("별친구 대화 - provider chunk를 순서대로 흘리고 DONE으로 닫는다")
    void streamCharacterTalk_providerChunks_areForwarded() {
        CharacterTalkGenerator generator = mock(CharacterTalkGenerator.class);
        AiCharacterTalkService service = service(300, generator);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("작은 별친구 하나만 ");
            consumer.accept("챙겨도 괜찮아.");
            return AiTokenUsage.empty();
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("NOVA", "오늘 좀 지쳤어"), emitter);

        assertEquals("작은 별친구 하나만 챙겨도 괜찮아.", emitter.joinedText());
        assertTrue(emitter.completed);
        assertFalse(emitter.fallbackUsed);
    }

    @Test
    @DisplayName("별친구 대화 - 무무는 안전한 발화 prefix를 먼저 스트리밍한다")
    void streamCharacterTalk_mumuStreamsSafeUtterancePrefix() {
        CharacterTalkGenerator generator = mock(CharacterTalkGenerator.class);
        AiCharacterTalkService service = service(300, generator);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("무무 ㅠㅠ... ");
            assertEquals(List.of("무무 ㅠㅠ... "), emitter.chunks);
            consumer.accept("(");
            assertEquals(List.of("무무 ㅠㅠ... "), emitter.chunks);
            consumer.accept("해석: 나 여기 있어. 지금 말이 조금 엉켜도 천천히 들려줘.)");
            return AiTokenUsage.empty();
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("MUMU", "오늘 좀 지쳤어"), emitter);

        assertEquals("무무 ㅠㅠ... (해석: 나 여기 있어. 지금 말이 조금 엉켜도 천천히 들려줘.)", emitter.joinedText());
        assertTrue(emitter.completed);
        assertFalse(emitter.fallbackUsed);
    }

    private AiCharacterTalkService service(
            int maxUserMessageLength,
            CharacterTalkGenerator generator
    ) {
        AiCharacterTalkProperties talkProperties = new AiCharacterTalkProperties();
        talkProperties.setMaxUserMessageLength(maxUserMessageLength);
        talkProperties.setMaxReplyLength(350);
        CharacterTalkHistoryService historyService = mock(CharacterTalkHistoryService.class);
        when(historyService.prepare(any())).thenReturn(preparedContext());

        return new AiCharacterTalkService(
                generator,
                new CharacterTalkValidationPolicy(talkProperties),
                historyService
        );
    }

    private PreparedCharacterTalkContext preparedContext() {
        return new PreparedCharacterTalkContext(
                mock(CharacterTalkSession.class),
                "session-1",
                true,
                LocalDateTime.now().plusMinutes(30),
                "[]",
                "[]",
                6,
                3,
                0
        );
    }

    private CharacterTalkGenerationCommand command(String characterType, String userMessage) {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                characterType,
                "MUMU".equals(characterType) ? "무다리" : characterType,
                userMessage,
                "TAP",
                "{}",
                "request-1"
        );
    }

    private static class CapturingEmitter implements CharacterTalkStreamEmitter {
        private final List<String> chunks = new ArrayList<>();
        private boolean completed;
        private boolean fallbackUsed;
        private AiErrorType errorType;
        private CharacterTalkStreamMetadata metadata;

        @Override
        public void emitMeta(CharacterTalkStreamMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void emitDelta(String text) {
            chunks.add(text);
        }

        @Override
        public void complete(boolean fallbackUsed, AiErrorType errorType, AiTokenUsage tokenUsage) {
            this.completed = true;
            this.fallbackUsed = fallbackUsed;
            this.errorType = errorType;
        }

        @Override
        public boolean hasEmittedDelta() {
            return !chunks.isEmpty();
        }

        private String joinedText() {
            return String.join("", chunks);
        }
    }
}
