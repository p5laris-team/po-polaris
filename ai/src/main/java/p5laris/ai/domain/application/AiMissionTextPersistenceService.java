package p5laris.ai.domain.application;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.event.AiEventLogEvent;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.AiUsageLog;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.repository.AiMissionGenerationRepository;
import p5laris.ai.domain.domain.repository.AiUsageLogRepository;

/**
 * AI 생성 결과와 사용 로그를 하나의 트랜잭션으로 저장하는 서비스다.
 *
 * 외부 AI provider가 붙으면 문구 생성은 네트워크 호출이 되므로 DB 트랜잭션 밖에서 끝내야 한다.
 * 이 클래스는 "이미 생성/검증이 끝난 결과"만 받아 짧은 DB 저장 트랜잭션을 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AiMissionTextPersistenceService {

    private final AiMissionGenerationRepository aiMissionGenerationRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * ai_mission_generations와 ai_usage_logs를 함께 저장한다.
     *
     * 둘 중 하나만 저장되면 추적 데이터가 어긋나므로 같은 트랜잭션으로 묶는다.
     */
    @Transactional
    public AiMissionGeneration saveGenerationAndUsageLog(
            MissionTextGenerationCommand command,
            PromptTemplate promptTemplate,
            String requestHash,
            String requestContextJson,
            String responseJson,
            AiGenerationStatus generationStatus,
            AiUsageStatus usageStatus,
            boolean fallbackUsed,
            String provider,
            String model,
            int latencyMs,
            AiErrorType errorType
    ) {
        AiMissionGeneration generation = AiMissionGeneration.create(
                command.userId(),
                command.characterId(),
                promptTemplate != null ? promptTemplate.getId() : null,
                command.requestId(),
                requestHash,
                requestContextJson,
                responseJson,
                command.missionTemplateId(),
                generationStatus,
                fallbackUsed,
                model,
                errorType
        );

        AiMissionGeneration savedGeneration = aiMissionGenerationRepository.save(generation);

        AiUsageLog usageLog = AiUsageLog.create(
                command.userId(),
                command.requestId(),
                model,
                latencyMs,
                usageStatus,
                errorType
        );
        aiUsageLogRepository.save(usageLog);

        // Prometheus Counter로 실시간 지표 수집
        meterRegistry.counter("ai.generation.requests",
                "status", usageStatus != null ? usageStatus.name() : "UNKNOWN",
                "fallback", String.valueOf(fallbackUsed),
                "error_type", errorType != null ? errorType.name() : "NONE",
                "model", model != null ? model : "unknown"
        ).increment();

        if (fallbackUsed) {
            eventPublisher.publishEvent(AiEventLogEvent.fallbackUsed(
                    command,
                    promptTemplate,
                    savedGeneration,
                    generationStatus,
                    usageStatus,
                    provider,
                    model,
                    latencyMs,
                    errorType
            ));
        }

        return savedGeneration;
    }
}
