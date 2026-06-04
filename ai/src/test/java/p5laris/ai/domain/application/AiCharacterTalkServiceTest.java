package p5laris.ai.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.domain.policy.CharacterTalkValidationPolicy;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.provider.AiProviderCircuitBreaker;
import p5laris.ai.domain.infrastructure.provider.GeminiCharacterTalkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiCharacterTalkServiceTest {

    @Test
    @DisplayName("별친구 대화 - 외부 provider가 꺼져 있으면 캐릭터별 fallback을 반환")
    void streamCharacterTalk_providerDisabled_returnsFallback() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(false, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("MUMU", "무무야 안녕"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.PROVIDER_ERROR, emitter.errorType);
        assertTrue(emitter.joinedText().startsWith("무"));
        assertTrue(emitter.joinedText().contains("(해석:"));
        verify(generator, never()).stream(any(), any());
        verify(rateLimiter, never()).checkAllowed(
                anyLong(),
                any(AiProviderType.class),
                anyString()
        );
    }

    @Test
    @DisplayName("별친구 대화 - 사용자 입력이 너무 길면 provider 호출 없이 fallback을 반환")
    void streamCharacterTalk_tooLongMessage_returnsFallback() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 3, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("NOVA", "너무 긴 메시지"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.INVALID_OUTPUT, emitter.errorType);
        assertTrue(emitter.joinedText().contains("좌표"));
        verify(generator, never()).stream(any(), any());
        verify(rateLimiter, never()).checkAllowed(
                anyLong(),
                any(AiProviderType.class),
                anyString()
        );
    }

    @Test
    @DisplayName("별친구 대화 - provider chunk를 순서대로 흘리고 DONE으로 닫는다")
    void streamCharacterTalk_providerChunks_areForwarded() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("작은 별빛 하나만 ");
            consumer.accept("챙겨도 괜찮아.");
            return null;
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("NOVA", "오늘 좀 피곤해"), emitter);

        assertEquals("작은 별빛 하나만 챙겨도 괜찮아.", emitter.joinedText());
        assertTrue(emitter.completed);
        assertTrue(!emitter.fallbackUsed);
        verify(rateLimiter).checkAllowed(anyLong(), any(AiProviderType.class), anyString());
    }

    @Test
    @DisplayName("별친구 대화 - 무무는 해석 라벨이 나올 때까지 초반 chunk를 buffer한다")
    void streamCharacterTalk_mumuBuffersUntilInterpretationLabel() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("무... 무무. ");
            assertTrue(emitter.chunks.isEmpty());
            consumer.accept("(해석: 무무가 잠깐 쉬어도 괜찮다고 하는 것 같아요.)");
            return null;
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("MUMU", "오늘 좀 피곤해"), emitter);

        assertEquals("무... 무무. (해석: 무무가 잠깐 쉬어도 괜찮다고 하는 것 같아요.)", emitter.joinedText());
        assertTrue(emitter.completed);
        assertTrue(!emitter.fallbackUsed);
    }

    private AiCharacterTalkService service(
            boolean providerEnabled,
            int maxUserMessageLength,
            GeminiCharacterTalkGenerator generator,
            AiRateLimiter rateLimiter
    ) {
        AiProviderProperties providerProperties = new AiProviderProperties();
        providerProperties.setEnabled(providerEnabled);
        providerProperties.setType("gemini");
        providerProperties.setModel("gemini-2.5-flash");

        AiCharacterTalkProperties talkProperties = new AiCharacterTalkProperties();
        talkProperties.setMaxUserMessageLength(maxUserMessageLength);
        talkProperties.setMaxReplyLength(350);

        return new AiCharacterTalkService(
                providerProperties,
                rateLimiter,
                new AiProviderCircuitBreaker(new AiCircuitBreakerProperties()),
                generator,
                new CharacterTalkValidationPolicy(talkProperties)
        );
    }

    private CharacterTalkGenerationCommand command(String characterType, String userMessage) {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                characterType,
                characterType,
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

        @Override
        public void emitDelta(String text) {
            chunks.add(text);
        }

        @Override
        public void complete(boolean fallbackUsed, AiErrorType errorType) {
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
