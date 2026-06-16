package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.ExternalCharacterTalkGenerator;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatingCharacterTalkGeneratorTest {

    @Test
    void providerDisabled_throwsFallbackBeforeRateLimitAndExternalCall() {
        AiProviderProperties properties = providerProperties(false, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingCharacterTalkGenerator generator = generator(properties, rateLimiter, externalGenerator);

        assertThatThrownBy(() -> generator.stream(validCommand(), ignored -> {
        }))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.PROVIDER_ERROR);

        assertThat(rateLimiter.called).isZero();
        assertThat(externalGenerator.called).isZero();
    }

    @Test
    void providerEnabled_callsRateLimitAndSelectedExternalGenerator() {
        AiProviderProperties properties = providerProperties(true, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingCharacterTalkGenerator generator = generator(properties, rateLimiter, externalGenerator);
        StringBuilder streamedText = new StringBuilder();

        AiTokenUsage tokenUsage = generator.stream(validCommand(), streamedText::append);

        assertThat(streamedText.toString()).isEqualTo("hello");
        assertThat(tokenUsage.totalTokens()).isEqualTo(3);
        assertThat(rateLimiter.called).isEqualTo(1);
        assertThat(rateLimiter.providerType).isEqualTo(AiProviderType.GEMINI);
        assertThat(externalGenerator.called).isEqualTo(1);
    }

    @Test
    void rateLimitFailure_doesNotCallExternalGenerator() {
        AiProviderProperties properties = providerProperties(true, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        rateLimiter.exception = new FallbackRequiredException(
                AiErrorType.RATE_LIMIT_UNAVAILABLE,
                "rate limit unavailable"
        );
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingCharacterTalkGenerator generator = generator(properties, rateLimiter, externalGenerator);

        assertThatThrownBy(() -> generator.stream(validCommand(), ignored -> {
        }))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.RATE_LIMIT_UNAVAILABLE);

        assertThat(externalGenerator.called).isZero();
    }

    @Test
    void unsupportedProvider_throwsFallback() {
        AiProviderProperties properties = providerProperties(true, "openai");
        DelegatingCharacterTalkGenerator generator = generator(
                properties,
                new SpyRateLimiter(),
                new SpyExternalGenerator()
        );

        assertThatThrownBy(() -> generator.stream(validCommand(), ignored -> {
        }))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.PROVIDER_ERROR);
    }

    private DelegatingCharacterTalkGenerator generator(
            AiProviderProperties properties,
            SpyRateLimiter rateLimiter,
            SpyExternalGenerator externalGenerator
    ) {
        return new DelegatingCharacterTalkGenerator(
                properties,
                rateLimiter,
                new AiProviderCircuitBreaker(circuitBreakerProperties()),
                List.of(externalGenerator)
        );
    }

    private AiProviderProperties providerProperties(boolean enabled, String type) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setEnabled(enabled);
        properties.setType(type);
        properties.setModel("gemini-2.5-flash");
        return properties;
    }

    private AiCircuitBreakerProperties circuitBreakerProperties() {
        AiCircuitBreakerProperties properties = new AiCircuitBreakerProperties();
        properties.setEnabled(false);
        return properties;
    }

    private CharacterTalkGenerationCommand validCommand() {
        return new CharacterTalkGenerationCommand(
                1001L,
                2001L,
                "NOVA",
                "NOVA",
                "오늘 좀 지쳤어",
                "CHAT",
                "{}",
                "request-1"
        );
    }

    private static class SpyRateLimiter implements AiRateLimiter {
        private int called;
        private AiProviderType providerType;
        private FallbackRequiredException exception;

        @Override
        public void checkAllowed(Long userId, AiProviderType providerType, String model) {
            called++;
            this.providerType = providerType;
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class SpyExternalGenerator implements ExternalCharacterTalkGenerator {
        private int called;

        @Override
        public AiProviderType providerType() {
            return AiProviderType.GEMINI;
        }

        @Override
        public AiTokenUsage stream(CharacterTalkGenerationCommand command, Consumer<String> chunkConsumer) {
            called++;
            chunkConsumer.accept("hello");
            return new AiTokenUsage(1, 2, 3);
        }
    }
}
