package p5laris.ai.domain.application.event;

import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ai 모듈에서 event-log 모듈로 보낼 비즈니스 이벤트 스냅샷이다.
 *
 * raw prompt나 raw response는 저장하지 않고,
 * fallback 비율과 실패 원인을 분석할 수 있는 요약 속성만 담는다.
 */
public record AiEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt
) {

    public static AiEventLogEvent fallbackUsed(
            MissionTextGenerationCommand command,
            PromptTemplate promptTemplate,
            AiMissionGeneration generation,
            AiGenerationStatus generationStatus,
            AiUsageStatus usageStatus,
            String provider,
            String model,
            int latencyMs,
            AiErrorType errorType
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", command.requestId());
        metadata.put("characterId", command.characterId());
        metadata.put("missionTemplateId", command.missionTemplateId());
        metadata.put("promptTemplateId", promptTemplate != null ? promptTemplate.getId() : null);
        metadata.put("promptCategory", promptTemplate != null ? promptTemplate.getCategory() : null);
        metadata.put("provider", normalizeProvider(provider));
        metadata.put("model", model);
        metadata.put("generationStatus", generationStatus);
        metadata.put("usageStatus", usageStatus);
        metadata.put("errorType", errorType);
        metadata.put("latencyMs", latencyMs);

        return new AiEventLogEvent(
                "AI_FALLBACK_USED",
                command.userId(),
                "AI_MISSION_GENERATION",
                generation.getId(),
                metadata,
                OffsetDateTime.now()
        );
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "LOCAL";
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }
}
