package p5laris.ai.domain.infrastructure.provider;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.ExternalMissionTextGenerator;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.application.generator.MissionTextGenerationOutput;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AI provider 선택을 담당하는 generator 라우터다.
 *
 * AiMissionTextService는 이 클래스만 바라보고, 실제 rule-based/Gemini/OpenAI 분기는 여기서 처리한다.
 */
@Primary
@Component
public class DelegatingMissionTextGenerator implements MissionTextGenerator {

    private final AiProviderProperties aiProviderProperties;
    private final RuleBasedMissionTextGenerator ruleBasedMissionTextGenerator;
    private final AiRateLimiter aiRateLimiter;
    private final AiProviderCircuitBreaker aiProviderCircuitBreaker;
    private final Map<AiProviderType, ExternalMissionTextGenerator> externalGenerators;

    public DelegatingMissionTextGenerator(
            AiProviderProperties aiProviderProperties,
            RuleBasedMissionTextGenerator ruleBasedMissionTextGenerator,
            AiRateLimiter aiRateLimiter,
            AiProviderCircuitBreaker aiProviderCircuitBreaker,
            List<ExternalMissionTextGenerator> externalGenerators
    ) {
        this.aiProviderProperties = aiProviderProperties;
        this.ruleBasedMissionTextGenerator = ruleBasedMissionTextGenerator;
        this.aiRateLimiter = aiRateLimiter;
        this.aiProviderCircuitBreaker = aiProviderCircuitBreaker;
        this.externalGenerators = toGeneratorMap(externalGenerators);
    }

    @Override
    public MissionTextCandidate generate(MissionTextGenerationCommand command) {
        return generateWithUsage(command).candidate();
    }

    @Override
    public MissionTextGenerationOutput generateWithUsage(MissionTextGenerationCommand command) {
        AiProviderType providerType = aiProviderProperties.providerType();

        if (!aiProviderProperties.isExternalEnabled()) {
            return MissionTextGenerationOutput.withoutUsage(ruleBasedMissionTextGenerator.generate(command));
        }

        ExternalMissionTextGenerator externalGenerator = externalGenerators.get(providerType);
        if (externalGenerator == null) {
            throw new FallbackRequiredException(
                    AiErrorType.PROVIDER_ERROR,
                    "지원하지 않거나 아직 구현되지 않은 AI provider입니다. provider=" + providerType
            );
        }

        aiRateLimiter.checkAllowed(command, providerType, aiProviderProperties.resolvedModel());
        return aiProviderCircuitBreaker.execute(
                providerType,
                aiProviderProperties.resolvedModel(),
                command.requestId(),
                () -> externalGenerator.generateWithUsage(command)
        );
    }

    private Map<AiProviderType, ExternalMissionTextGenerator> toGeneratorMap(
            List<ExternalMissionTextGenerator> generators
    ) {
        Map<AiProviderType, ExternalMissionTextGenerator> generatorMap = new EnumMap<>(AiProviderType.class);
        for (ExternalMissionTextGenerator generator : generators) {
            generatorMap.put(generator.providerType(), generator);
        }
        return generatorMap;
    }
}
