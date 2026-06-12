package p5laris.ai.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;
import p5laris.ai.domain.domain.repository.CharacterTalkMessageRepository;
import p5laris.ai.domain.domain.repository.CharacterTalkSessionRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;

import java.time.LocalDateTime;

/**
 * 별친구 대화 메시지를 세션 단위로 순번 충돌 없이 저장한다.
 */
@Service
@RequiredArgsConstructor
public class CharacterTalkMessageWriter {

    private final CharacterTalkSessionRepository sessionRepository;
    private final CharacterTalkMessageRepository messageRepository;

    @Transactional
    public void recordUserMessage(
            Long sessionId,
            CharacterTalkGenerationCommand command,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        CharacterTalkSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new AiException(AiErrorCode.AI_INVALID_REQUEST));

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
    }

    @Transactional
    public void recordAssistantResponse(
            Long sessionId,
            CharacterTalkGenerationCommand command,
            String assistantText,
            boolean fallbackUsed,
            AiTokenUsage tokenUsage
    ) {
        if (assistantText == null || assistantText.isBlank()) {
            return;
        }

        CharacterTalkSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElse(null);
        if (session == null) {
            return;
        }

        CharacterTalkMessage message = CharacterTalkMessage.create(
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
        messageRepository.save(message);
    }

    private String trimForStorage(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= 2_000) {
            return trimmed;
        }
        return trimmed.substring(0, 2_000);
    }
}
