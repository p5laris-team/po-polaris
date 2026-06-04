package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.domain.enums.AiProviderType;

/**
 * 외부 AI provider 호출 직전에 호출량 제한을 확인하는 포트다.
 *
 * 구현체가 제한 초과 또는 저장소 장애를 판단하면 FallbackRequiredException을 던지고,
 * 상위 서비스는 Gemini/OpenAI 호출 대신 fallback 문구를 저장한다.
 */
public interface AiRateLimiter {

    void checkAllowed(Long userId, AiProviderType providerType, String model);

    default void checkAllowed(MissionTextGenerationCommand command, AiProviderType providerType, String model) {
        checkAllowed(command.userId(), providerType, model);
    }
}
