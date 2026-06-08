package p5laris.ai.domain.application.dto;

import p5laris.ai.domain.domain.entity.CharacterTalkSession;

import java.time.LocalDateTime;

/**
 * provider 호출 직전에 확정한 대화 세션, 최근 대화 window, 장기 기억 검색 결과다.
 *
 * 세션 저장과 provider 호출을 분리해 원격 호출이 긴 DB 트랜잭션 안에 들어가지 않도록 한다.
 */
public record PreparedCharacterTalkContext(
        CharacterTalkSession session,
        String sessionId,
        boolean newSession,
        LocalDateTime expiresAt,
        String conversationHistoryJson,
        String memoryContextJson,
        int historyWindowTurns,
        int memorySearchTopK,
        int memoryHitCount
) {

    public CharacterTalkStreamMetadata toMetadata(String requestId) {
        return new CharacterTalkStreamMetadata(
                requestId,
                sessionId,
                newSession,
                expiresAt,
                historyWindowTurns,
                memorySearchTopK,
                memoryHitCount
        );
    }
}
