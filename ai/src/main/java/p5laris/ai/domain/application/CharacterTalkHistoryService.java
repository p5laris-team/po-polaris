package p5laris.ai.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingResult;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.memory.CharacterTalkMemoryHit;
import p5laris.ai.domain.application.memory.EmbeddingVectorUtils;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;
import p5laris.ai.domain.domain.enums.CharacterTalkSessionStatus;
import p5laris.ai.domain.domain.repository.CharacterTalkMessageRepository;
import p5laris.ai.domain.domain.repository.CharacterTalkSessionRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;
import p5laris.ai.domain.infrastructure.repository.CharacterTalkMemoryJdbcRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 별친구 대화의 멀티턴 세션, 최근 대화 window, 장기 기억 검색을 담당한다.
 *
 * DB 기록과 외부 AI provider 호출을 분리해 대화 생성 트랜잭션이 길어지지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterTalkHistoryService {

    private static final int SUMMARY_MAX_LENGTH = 700;

    private final CharacterTalkSessionRepository sessionRepository;
    private final CharacterTalkMessageRepository messageRepository;
    private final CharacterTalkMemoryJdbcRepository memoryJdbcRepository;
    private final AiTextEmbeddingService textEmbeddingService;
    private final AiCharacterTalkProperties properties;
    private final AiEmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;

    public PreparedCharacterTalkContext prepare(CharacterTalkGenerationCommand command) {
        LocalDateTime now = LocalDateTime.now();
        summarizeExpiredSessions(command, now);

        SessionResolution resolution = resolveSession(command, now);
        CharacterTalkSession session = resolution.session();
        LocalDateTime nextExpiresAt = now.plusMinutes(properties.normalizedSessionTtlMinutes());

        List<CharacterTalkMessage> previousMessages = findPromptWindowMessages(session);
        List<CharacterTalkMemoryHit> memoryHits = searchMemories(command);
        String historyJson = toHistoryJson(previousMessages);
        String memoryJson = toMemoryJson(memoryHits);

        recordUserMessage(session, command, now, nextExpiresAt);

        return new PreparedCharacterTalkContext(
                session,
                session.getSessionId(),
                resolution.newSession(),
                nextExpiresAt,
                historyJson,
                memoryJson,
                properties.normalizedHistoryMaxTurns(),
                properties.normalizedMemorySearchTopK(),
                memoryHits.size()
        );
    }

    @Transactional
    public void recordAssistantResponse(
            PreparedCharacterTalkContext context,
            CharacterTalkGenerationCommand command,
            String assistantText,
            boolean fallbackUsed,
            AiTokenUsage tokenUsage
    ) {
        if (context == null || assistantText == null || assistantText.isBlank()) {
            return;
        }

        CharacterTalkSession session = sessionRepository.findById(context.session().getId())
                .orElse(null);
        if (session == null) {
            return;
        }

        CharacterTalkMessage assistantMessage = CharacterTalkMessage.create(
                session,
                CharacterTalkMessageRole.ASSISTANT,
                trimForStorage(assistantText),
                session.nextSequence(),
                command.requestId(),
                fallbackUsed
        );
        session.recordAssistantMessage(
                tokenUsage != null ? tokenUsage.promptTokens() : null,
                tokenUsage != null ? tokenUsage.completionTokens() : null,
                tokenUsage != null ? tokenUsage.totalTokens() : null
        );
        messageRepository.save(assistantMessage);
        sessionRepository.save(session);
    }

    @Transactional
    public void cleanupExpiredData() {
        if (!properties.isCleanupEnabled()) {
            return;
        }

        LocalDateTime messageCutoff = LocalDateTime.now().minusHours(properties.normalizedMessageRetentionHours());
        LocalDateTime sessionCutoff = LocalDateTime.now().minusDays(properties.normalizedSessionRetentionDays());
        int deletedMessages = messageRepository.deleteMessagesBefore(messageCutoff);
        int deletedSessions = sessionRepository.deleteClosedSessionsBefore(
                CharacterTalkSessionStatus.ACTIVE,
                sessionCutoff
        );
        if (deletedMessages > 0 || deletedSessions > 0) {
            log.info("별친구 대화 보관 정책 정리 완료. deletedMessages={}, deletedSessions={}",
                    deletedMessages, deletedSessions);
        }
    }

    private void summarizeExpiredSessions(CharacterTalkGenerationCommand command, LocalDateTime now) {
        List<CharacterTalkSession> sessions = sessionRepository
                .findTop10ByUserIdAndCharacterIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        command.userId(),
                        command.characterId(),
                        CharacterTalkSessionStatus.ACTIVE,
                        now
                );
        for (CharacterTalkSession session : sessions) {
            summarizeSession(command, session);
        }
    }

    private void summarizeSession(CharacterTalkGenerationCommand command, CharacterTalkSession session) {
        try {
            List<CharacterTalkMessage> messages = messageRepository.findBySessionIdOrderBySequenceAsc(session.getId());
            String summary = buildSummary(messages);
            if (summary.isBlank()) {
                markExpired(session);
                return;
            }

            TextEmbeddingResult embedding = textEmbeddingService.generateTextEmbedding(new TextEmbeddingCommand(
                    command.userId(),
                    summary,
                    embeddingProperties.resolvedModel(),
                    embeddingProperties.getDimension(),
                    "character-talk-memory-" + session.getSessionId()
            ));
            List<Float> normalized = EmbeddingVectorUtils.normalize(embedding.values(), embedding.dimension());
            memoryJdbcRepository.upsertSessionSummary(
                    session.getUserId(),
                    session.getCharacterId(),
                    session.getId(),
                    summary,
                    embedding.model(),
                    embedding.dimension(),
                    normalized
            );
            markMemoryReady(session);
        } catch (Exception e) {
            log.warn("별친구 대화 세션 기억화 실패. sessionId={}, characterId={}, 예외클래스={}",
                    session.getSessionId(), session.getCharacterId(), e.getClass().getSimpleName());
            markExpired(session);
        }
    }

    protected void markExpired(CharacterTalkSession session) {
        session.markExpired();
        sessionRepository.save(session);
    }

    protected void markMemoryReady(CharacterTalkSession session) {
        session.markMemoryReady(LocalDateTime.now());
        sessionRepository.save(session);
    }

    protected SessionResolution resolveSession(CharacterTalkGenerationCommand command, LocalDateTime now) {
        LocalDateTime expiresAt = now.plusMinutes(properties.normalizedSessionTtlMinutes());
        String requestedSessionId = normalizeText(command.sessionId());
        if (!requestedSessionId.isBlank()) {
            return sessionRepository.findBySessionId(requestedSessionId)
                    .map(session -> resolveRequestedSession(command, session, now, expiresAt))
                    .orElseGet(() -> createNewSession(command, now, expiresAt));
        }

        return sessionRepository
                .findFirstByUserIdAndCharacterIdAndStatusAndExpiresAtAfterOrderByLastMessageAtDesc(
                        command.userId(),
                        command.characterId(),
                        CharacterTalkSessionStatus.ACTIVE,
                        now
                )
                .map(session -> {
                    session.refresh(now, expiresAt);
                    return new SessionResolution(sessionRepository.save(session), false);
                })
                .orElseGet(() -> createNewSession(command, now, expiresAt));
    }

    private SessionResolution resolveRequestedSession(
            CharacterTalkGenerationCommand command,
            CharacterTalkSession session,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        if (!session.isOwnedBy(command.userId(), command.characterId())) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
        if (!session.isActiveAt(now)) {
            return createNewSession(command, now, expiresAt);
        }
        session.refresh(now, expiresAt);
        return new SessionResolution(sessionRepository.save(session), false);
    }

    private SessionResolution createNewSession(
            CharacterTalkGenerationCommand command,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        CharacterTalkSession session = CharacterTalkSession.create(
                UUID.randomUUID().toString(),
                command.userId(),
                command.characterId(),
                normalizeCharacterType(command.characterType()),
                now,
                expiresAt
        );
        return new SessionResolution(sessionRepository.save(session), true);
    }

    protected void recordUserMessage(
            CharacterTalkSession session,
            CharacterTalkGenerationCommand command,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        CharacterTalkMessage message = CharacterTalkMessage.create(
                session,
                CharacterTalkMessageRole.USER,
                trimForStorage(command.userMessage()),
                session.nextSequence(),
                command.requestId(),
                false
        );
        session.recordUserMessage(now, expiresAt);
        messageRepository.save(message);
        sessionRepository.save(session);
    }

    private List<CharacterTalkMessage> findPromptWindowMessages(CharacterTalkSession session) {
        int maxMessages = properties.normalizedHistoryMaxTurns() * 2;
        List<CharacterTalkMessage> recent = messageRepository.findRecentMessages(
                session.getId(),
                PageRequest.of(0, maxMessages)
        );
        List<CharacterTalkMessage> selected = new ArrayList<>(recent);
        selected.sort(Comparator.comparingInt(CharacterTalkMessage::getSequence));
        return selected;
    }

    private List<CharacterTalkMemoryHit> searchMemories(CharacterTalkGenerationCommand command) {
        int topK = properties.normalizedMemorySearchTopK();
        if (topK <= 0 || command.userMessage() == null || command.userMessage().isBlank()) {
            return List.of();
        }
        try {
            TextEmbeddingResult embedding = textEmbeddingService.generateTextEmbedding(new TextEmbeddingCommand(
                    command.userId(),
                    command.userMessage(),
                    embeddingProperties.resolvedModel(),
                    embeddingProperties.getDimension(),
                    "character-talk-memory-query-" + command.requestId()
            ));
            List<Float> normalized = EmbeddingVectorUtils.normalize(embedding.values(), embedding.dimension());
            return memoryJdbcRepository.searchSimilar(
                    command.userId(),
                    command.characterId(),
                    embedding.model(),
                    embedding.dimension(),
                    normalized,
                    topK,
                    properties.normalizedMemorySimilarityThreshold()
            );
        } catch (Exception e) {
            log.warn("별친구 대화 장기 기억 검색 실패. requestId={}, characterId={}, 예외클래스={}",
                    command.requestId(), command.characterId(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    private String toHistoryJson(List<CharacterTalkMessage> messages) {
        List<Map<String, Object>> body = messages.stream()
                .map(message -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    item.put("content", message.getContent());
                    item.put("sequence", message.getSequence());
                    return item;
                })
                .toList();
        return toJson(body);
    }

    private String toMemoryJson(List<CharacterTalkMemoryHit> hits) {
        List<Map<String, Object>> body = hits.stream()
                .map(hit -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("summary", hit.summary());
                    item.put("similarity", Math.max(0.0d, 1.0d - hit.distance()));
                    item.put("createdAt", hit.createdAt().toString());
                    return item;
                })
                .toList();
        return toJson(body);
    }

    private String buildSummary(List<CharacterTalkMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("이전 대화 요약: ");
        messages.stream()
                .sorted(Comparator.comparingInt(CharacterTalkMessage::getSequence))
                .limit(12)
                .forEach(message -> builder
                        .append(message.getRole() == CharacterTalkMessageRole.USER ? "사용자: " : "별친구: ")
                        .append(trimForSummary(message.getContent()))
                        .append(' '));
        String summary = builder.toString().trim();
        if (summary.length() <= SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, SUMMARY_MAX_LENGTH);
    }

    private String trimForSummary(String value) {
        String trimmed = normalizeText(value);
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 120);
    }

    private String trimForStorage(String value) {
        String trimmed = normalizeText(value);
        if (trimmed.length() <= 2_000) {
            return trimmed;
        }
        return trimmed.substring(0, 2_000);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String normalizeCharacterType(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record SessionResolution(CharacterTalkSession session, boolean newSession) {
    }
}
