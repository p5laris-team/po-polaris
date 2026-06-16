package p5laris.ai.domain.infrastructure.provider;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.CharacterTalkGenerator;
import p5laris.ai.domain.application.generator.ExternalCharacterTalkGenerator;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Primary
@Component
public class DelegatingCharacterTalkGenerator implements CharacterTalkGenerator {

    private final AiProviderProperties aiProviderProperties;
    private final AiRateLimiter aiRateLimiter;
    private final AiProviderCircuitBreaker aiProviderCircuitBreaker;
    private final Map<AiProviderType, ExternalCharacterTalkGenerator> externalGenerators;

    public DelegatingCharacterTalkGenerator(
            AiProviderProperties aiProviderProperties,
            AiRateLimiter aiRateLimiter,
            AiProviderCircuitBreaker aiProviderCircuitBreaker,
            List<ExternalCharacterTalkGenerator> externalGenerators
    ) {
        this.aiProviderProperties = aiProviderProperties;
        this.aiRateLimiter = aiRateLimiter;
        this.aiProviderCircuitBreaker = aiProviderCircuitBreaker;
        this.externalGenerators = toGeneratorMap(externalGenerators);
    }

    @Override
    public AiTokenUsage stream(CharacterTalkGenerationCommand command, Consumer<String> chunkConsumer) {
        AiProviderType providerType = aiProviderProperties.providerType();
        if (!aiProviderProperties.isExternalEnabled()) {
            throw new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "External AI provider is disabled.");
        }

        ExternalCharacterTalkGenerator externalGenerator = externalGenerators.get(providerType);
        if (externalGenerator == null) {
            throw new FallbackRequiredException(
                    AiErrorType.PROVIDER_ERROR,
                    "Unsupported character talk AI provider. provider=" + providerType
            );
        }

        aiRateLimiter.checkAllowed(command.userId(), providerType, aiProviderProperties.resolvedModel());
        return aiProviderCircuitBreaker.execute(
                providerType,
                aiProviderProperties.resolvedModel(),
                command.requestId(),
                () -> externalGenerator.stream(command, chunkConsumer)
        );
    }

    private Map<AiProviderType, ExternalCharacterTalkGenerator> toGeneratorMap(
            List<ExternalCharacterTalkGenerator> generators
    ) {
        Map<AiProviderType, ExternalCharacterTalkGenerator> generatorMap = new EnumMap<>(AiProviderType.class);
        for (ExternalCharacterTalkGenerator generator : generators) {
            generatorMap.put(generator.providerType(), generator);
        }
        return generatorMap;
    }
}
