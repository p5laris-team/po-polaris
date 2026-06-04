package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.ExternalMissionTextGenerator;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.domain.policy.CharacterTonePolicy;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatingMissionTextGeneratorTest {

    @Test
    void provider가_비활성화되어_있으면_rule_based_generator를_사용한다() {
        AiProviderProperties properties = providerProperties(false, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingMissionTextGenerator generator = new DelegatingMissionTextGenerator(
                properties,
                new RuleBasedMissionTextGenerator(new CharacterTonePolicy()),
                rateLimiter,
                new AiProviderCircuitBreaker(circuitBreakerProperties(false)),
                List.of(externalGenerator)
        );

        MissionTextCandidate candidate = generator.generate(validCommand("NOVA"));

        assertThat(candidate.characterMessage()).contains("천천히");
        assertThat(rateLimiter.called).isZero();
        assertThat(externalGenerator.called).isZero();
    }

    @Test
    void Gemini_provider가_활성화되어_있으면_rate_limit_확인_후_외부_generator를_호출한다() {
        AiProviderProperties properties = providerProperties(true, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingMissionTextGenerator generator = new DelegatingMissionTextGenerator(
                properties,
                new RuleBasedMissionTextGenerator(new CharacterTonePolicy()),
                rateLimiter,
                new AiProviderCircuitBreaker(circuitBreakerProperties(false)),
                List.of(externalGenerator)
        );

        MissionTextCandidate candidate = generator.generate(validCommand("NOVA"));

        assertThat(candidate.characterMessage()).isEqualTo("gemini message");
        assertThat(rateLimiter.called).isEqualTo(1);
        assertThat(rateLimiter.providerType).isEqualTo(AiProviderType.GEMINI);
        assertThat(externalGenerator.called).isEqualTo(1);
    }

    @Test
    void rate_limit_저장소를_쓸_수_없으면_외부_generator를_호출하지_않는다() {
        AiProviderProperties properties = providerProperties(true, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        rateLimiter.exception = new FallbackRequiredException(
                AiErrorType.RATE_LIMIT_UNAVAILABLE,
                "Redis를 사용할 수 없습니다."
        );
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        DelegatingMissionTextGenerator generator = new DelegatingMissionTextGenerator(
                properties,
                new RuleBasedMissionTextGenerator(new CharacterTonePolicy()),
                rateLimiter,
                new AiProviderCircuitBreaker(circuitBreakerProperties(false)),
                List.of(externalGenerator)
        );

        assertThatThrownBy(() -> generator.generate(validCommand("NOVA")))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.RATE_LIMIT_UNAVAILABLE);
        assertThat(externalGenerator.called).isZero();
    }

    @Test
    void 외부_provider_실패가_반복되면_서킷을_열고_추가_외부호출을_막는다() {
        AiProviderProperties properties = providerProperties(true, "gemini");
        SpyRateLimiter rateLimiter = new SpyRateLimiter();
        SpyExternalGenerator externalGenerator = new SpyExternalGenerator();
        externalGenerator.exception = new FallbackRequiredException(
                AiErrorType.PROVIDER_ERROR,
                "Gemini 호출 실패"
        );
        DelegatingMissionTextGenerator generator = new DelegatingMissionTextGenerator(
                properties,
                new RuleBasedMissionTextGenerator(new CharacterTonePolicy()),
                rateLimiter,
                new AiProviderCircuitBreaker(circuitBreakerProperties(true)),
                List.of(externalGenerator)
        );

        assertThatThrownBy(() -> generator.generate(validCommand("NOVA")))
                .isInstanceOf(FallbackRequiredException.class);
        assertThatThrownBy(() -> generator.generate(validCommand("NOVA")))
                .isInstanceOf(FallbackRequiredException.class);

        assertThatThrownBy(() -> generator.generate(validCommand("NOVA")))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.PROVIDER_ERROR);
        assertThat(externalGenerator.called).isEqualTo(2);
    }

    private AiProviderProperties providerProperties(boolean enabled, String type) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setEnabled(enabled);
        properties.setType(type);
        properties.setModel("gemini-2.5-flash");
        return properties;
    }

    private AiCircuitBreakerProperties circuitBreakerProperties(boolean enabled) {
        AiCircuitBreakerProperties properties = new AiCircuitBreakerProperties();
        properties.setEnabled(enabled);
        properties.setSlidingWindowSize(2);
        properties.setMinimumNumberOfCalls(2);
        properties.setFailureRateThreshold(50);
        properties.setSlowCallDurationMs(1_000);
        properties.setSlowCallRateThreshold(100);
        properties.setWaitDurationOpenMs(60_000);
        return properties;
    }

    private MissionTextGenerationCommand validCommand(String characterType) {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                characterType,
                3001L,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
                "물 마시고 나서 기분이 조금 달라졌어?",
                "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
                "{}",
                "{}",
                UUID.randomUUID().toString()
        );
    }

    private static class SpyRateLimiter implements AiRateLimiter {
        private int called;
        private AiProviderType providerType;
        private FallbackRequiredException exception;

        @Override
        public void checkAllowed(
                Long userId,
                AiProviderType providerType,
                String model
        ) {
            called++;
            this.providerType = providerType;
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class SpyExternalGenerator implements ExternalMissionTextGenerator {
        private int called;
        private FallbackRequiredException exception;

        @Override
        public AiProviderType providerType() {
            return AiProviderType.GEMINI;
        }

        @Override
        public MissionTextCandidate generate(MissionTextGenerationCommand command) {
            called++;
            if (exception != null) {
                throw exception;
            }
            return new MissionTextCandidate(
                    "gemini title",
                    "gemini description",
                    "gemini message",
                    "gemini question",
                    "gemini response",
                    "BASIC_ROUTINE",
                    "EASY"
            );
        }
    }
}
