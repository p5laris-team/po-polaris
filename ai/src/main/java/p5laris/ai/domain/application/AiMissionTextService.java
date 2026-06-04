package p5laris.ai.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.policy.MissionTextValidationPolicy;
import p5laris.ai.domain.domain.repository.AiMissionGenerationRepository;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.provider.RuleBasedMissionTextGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AI 자율 미션 후보 생성 유스케이스의 중심 서비스다.
 *
 * 이 서비스는 "어떤 provider를 썼는지"보다 Polaris 정책을 우선한다.
 * provider가 만든 미션 후보를 검증하고 저장하되, 실제 보상과 최종 저장 여부는 mission 모듈이 결정한다.
 */
@Service
@RequiredArgsConstructor
public class AiMissionTextService {

    private final MissionTextGenerator missionTextGenerator;
    private final RuleBasedMissionTextGenerator ruleBasedMissionTextGenerator;
    private final MissionTextValidationPolicy missionTextValidationPolicy;
    private final AiMissionTextPersistenceService aiMissionTextPersistenceService;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AiMissionGenerationRepository aiMissionGenerationRepository;
    private final AiProviderProperties aiProviderProperties;
    private final ObjectMapper objectMapper;

    /**
     * 개인화 context를 바탕으로 자율 미션 후보를 생성한다.
     *
     * 실제 provider 선택과 rate limit은 MissionTextGenerator 구현체가 담당한다.
     * 이 서비스는 성공/fallback 결과를 검증하고 저장하는 흐름만 책임진다.
     */
    public MissionTextGenerationResult generateMissionTexts(MissionTextGenerationCommand command) {
        validateRequest(command);
        String requestHash = requestHash(command);

        return aiMissionGenerationRepository.findByRequestId(command.requestId())
                .map(existingGeneration -> toResult(validateReusableGeneration(existingGeneration, requestHash)))
                .orElseGet(() -> generateAndSave(command, requestHash));
    }

    /**
     * 아직 처리되지 않은 요청이면 실제 generator를 실행하고 결과를 저장한다.
     *
     * 같은 requestId가 동시에 들어와 DB unique 제약에 걸리면 이미 저장된 결과를 다시 조회해 재사용한다.
     */
    private MissionTextGenerationResult generateAndSave(MissionTextGenerationCommand command, String requestHash) {
        long startedAt = System.nanoTime();
        PromptTemplate promptTemplate = findMissionGenerationPromptTemplate();
        MissionTextCandidate candidate;
        AiGenerationStatus status;
        AiUsageStatus usageStatus;
        boolean fallbackUsed;
        AiErrorType errorType = null;

        try {
            candidate = missionTextGenerator.generate(command);
            missionTextValidationPolicy.validate(candidate, command.characterType());
            status = AiGenerationStatus.SUCCESS;
            usageStatus = AiUsageStatus.SUCCESS;
            fallbackUsed = false;
        } catch (FallbackRequiredException e) {
            errorType = e.getErrorType();
            candidate = fallbackCandidate(command);
            validateFallbackCandidate(command, candidate);
            status = AiGenerationStatus.FALLBACK;
            usageStatus = errorType.isRateLimitFailure()
                    ? AiUsageStatus.RATE_LIMITED
                    : AiUsageStatus.FALLBACK;
            fallbackUsed = true;
        }

        AiMissionGeneration generation = saveResult(
                command,
                promptTemplate,
                requestHash,
                candidate,
                status,
                usageStatus,
                fallbackUsed,
                startedAt,
                errorType
        );

        return toResult(generation);
    }

    // 저장된 AI 생성 결과를 application result로 되돌린다. 재시도 요청도 이 경로로 기존 결과를 그대로 받는다.
    private MissionTextGenerationResult toResult(AiMissionGeneration generation) {
        JsonNode response = parseRequiredJson(generation.getResponseJson());
        return new MissionTextGenerationResult(
                generation.getId(),
                generation.getStatus(),
                requiredText(response, "title"),
                requiredText(response, "description"),
                requiredText(response, "characterMessage"),
                requiredText(response, "completionQuestion"),
                requiredText(response, "completionCharacterResponse"),
                requiredText(response, "category"),
                requiredText(response, "difficulty"),
                generation.isFallbackUsed(),
                generation.getErrorType(),
                generation.getRequestId()
        );
    }

    // gRPC로 들어온 요청이 AI 자율 미션 후보 생성에 필요한 최소 정보를 모두 갖고 있는지 확인한다.
    private void validateRequest(MissionTextGenerationCommand command) {
        if (command == null
                || isNotPositive(command.userId())
                || isNotPositive(command.characterId())
                || isNotPositive(command.missionTemplateId())
                || isBlank(command.characterType())
                || isBlank(command.baseTitle())
                || isBlank(command.baseDescription())
                || isBlank(command.category())
                || isBlank(command.difficulty())
                || isBlank(command.fallbackCharacterMessage())
                || isBlank(command.fallbackQuestion())
                || isBlank(command.fallbackCompletionResponse())
                || isBlank(command.requestId())) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }

    // 같은 request_id가 같은 요청 본문으로 다시 들어오면 기존 결과를 재사용한다.
    // 같은 request_id인데 내용이 달라졌다면 호출자가 멱등키를 잘못 재사용한 것이므로 충돌로 처리한다.
    private AiMissionGeneration validateReusableGeneration(AiMissionGeneration generation, String requestHash) {
        if (!generation.getRequestHash().equals(requestHash)) {
            throw new AiException(AiErrorCode.AI_REQUEST_CONFLICT);
        }
        return generation;
    }

    // 현재 활성화된 미션 생성 프롬프트 템플릿을 가져온다. 없으면 null로 저장해도 생성 자체는 가능하다.
    private PromptTemplate findMissionGenerationPromptTemplate() {
        return promptTemplateRepository
                .findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory.MISSION_GENERATION)
                .orElse(null);
    }

    // provider 결과가 실패하면 캐릭터별 rule-based 문구를 먼저 쓰고, 캐릭터 타입 자체가 알 수 없을 때만 seed fallback을 사용한다.
    private MissionTextCandidate fallbackCandidate(MissionTextGenerationCommand command) {
        try {
            return ruleBasedMissionTextGenerator.generate(command);
        } catch (FallbackRequiredException e) {
            // 지원하지 않는 캐릭터 타입이면 캐릭터별 rule-based 문구를 만들 수 없으므로 seed fallback을 사용한다.
        }

        return new MissionTextCandidate(
                command.baseTitle(),
                command.baseDescription(),
                command.fallbackCharacterMessage(),
                command.fallbackQuestion(),
                command.fallbackCompletionResponse(),
                command.category(),
                command.difficulty()
        );
    }

    // fallback 문구도 사용자에게 그대로 노출되므로 동일한 안전 검증을 통과해야 한다.
    private void validateFallbackCandidate(MissionTextGenerationCommand command, MissionTextCandidate candidate) {
        try {
            missionTextValidationPolicy.validate(candidate, command.characterType());
        } catch (FallbackRequiredException e) {
            throw new AiException(AiErrorCode.AI_FALLBACK_INVALID);
        }
    }

    private AiMissionGeneration saveResult(
            MissionTextGenerationCommand command,
            PromptTemplate promptTemplate,
            String requestHash,
            MissionTextCandidate candidate,
            AiGenerationStatus status,
            AiUsageStatus usageStatus,
            boolean fallbackUsed,
            long startedAt,
            AiErrorType errorType
    ) {
        try {
            return aiMissionTextPersistenceService.saveGenerationAndUsageLog(
                    command,
                    promptTemplate,
                    requestHash,
                    toRequestContextJson(command),
                    toResponseJson(candidate),
                    status,
                    usageStatus,
                    fallbackUsed,
                    aiProviderProperties.getType(),
                    aiProviderProperties.resolvedModel(),
                    elapsedMillis(startedAt),
                    errorType
            );
        } catch (DataIntegrityViolationException e) {
            return aiMissionGenerationRepository.findByRequestId(command.requestId())
                    .map(existingGeneration -> validateReusableGeneration(existingGeneration, requestHash))
                    .orElseThrow(() -> new AiException(AiErrorCode.AI_DUPLICATED_REQUEST));
        }
    }

    // request_id를 제외한 요청 본문을 안정적인 JSON으로 만든 뒤 SHA-256 해시를 계산한다.
    // 같은 request_id가 정말 같은 요청을 의미하는지 비교하기 위한 운영 안전장치다.
    private String requestHash(MissionTextGenerationCommand command) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("userId", command.userId());
        source.put("characterId", command.characterId());
        source.put("characterType", command.characterType());
        source.put("missionTemplateId", command.missionTemplateId());
        source.put("baseTitle", command.baseTitle());
        source.put("baseDescription", command.baseDescription());
        source.put("category", command.category());
        source.put("difficulty", command.difficulty());
        source.put("fallbackCharacterMessage", command.fallbackCharacterMessage());
        source.put("fallbackQuestion", command.fallbackQuestion());
        source.put("fallbackCompletionResponse", command.fallbackCompletionResponse());
        source.put("onboardingContext", canonicalJsonValue(parseJsonOrEmptyObject(command.onboardingContextJson())));
        source.put("recentMissionContext", canonicalJsonValue(parseJsonOrEmptyObject(command.recentMissionContextJson())));

        return sha256(toJson(source));
    }

    // AI 입력 context를 JSONB에 저장하기 위한 snapshot으로 만든다.
    private String toRequestContextJson(MissionTextGenerationCommand command) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("characterType", command.characterType());
        context.put("baseTitle", command.baseTitle());
        context.put("baseDescription", command.baseDescription());
        context.put("category", command.category());
        context.put("difficulty", command.difficulty());
        context.put("fallbackCharacterMessage", command.fallbackCharacterMessage());
        context.put("fallbackQuestion", command.fallbackQuestion());
        context.put("fallbackCompletionResponse", command.fallbackCompletionResponse());
        context.put("onboardingContext", parseJsonOrEmptyObject(command.onboardingContextJson()));
        context.put("recentMissionContext", parseJsonOrEmptyObject(command.recentMissionContextJson()));
        context.put("requestId", command.requestId());
        return toJson(context);
    }

    // AI 출력도 JSONB에 저장해 나중에 fallback 비율과 문구 품질을 추적할 수 있게 한다.
    private String toResponseJson(MissionTextCandidate candidate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", candidate.title());
        response.put("description", candidate.description());
        response.put("characterMessage", candidate.characterMessage());
        response.put("completionQuestion", candidate.completionQuestion());
        response.put("completionCharacterResponse", candidate.completionCharacterResponse());
        response.put("category", candidate.category());
        response.put("difficulty", candidate.difficulty());
        return toJson(response);
    }

    // Map을 JSON 문자열로 바꾼다. 이 문자열은 DB의 JSONB 컬럼에 저장된다.
    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AiException(AiErrorCode.AI_GENERATION_FAILED);
        }
    }

    private JsonNode parseRequiredJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new AiException(AiErrorCode.AI_GENERATION_FAILED);
        }
    }

    // context 문자열이 JSON이면 구조를 살려 저장하고, 비어 있으면 빈 객체로 저장한다.
    private JsonNode parseJsonOrEmptyObject(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.asText().isBlank()) {
            throw new AiException(AiErrorCode.AI_GENERATION_FAILED);
        }
        return field.asText();
    }

    // JSON object의 field 순서를 정렬해 같은 의미의 context가 같은 해시를 갖도록 만든다.
    private Object canonicalJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sorted.put(field.getKey(), canonicalJsonValue(field.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(canonicalJsonValue(value)));
            return values;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new AiException(AiErrorCode.AI_GENERATION_FAILED);
        }
    }

    // 사용 로그에 남길 provider 처리 시간을 millisecond로 계산한다.
    private int elapsedMillis(long startedAt) {
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        if (elapsedMillis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) elapsedMillis;
    }

    private boolean isNotPositive(Long value) {
        return value == null || value <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
