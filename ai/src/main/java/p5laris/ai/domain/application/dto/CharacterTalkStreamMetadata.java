package p5laris.ai.domain.application.dto;

import java.time.LocalDateTime;

/**
 * 별친구 대화 SSE/gRPC stream의 첫 meta 이벤트에 담을 세션/윈도우 정보다.
 *
 * 프론트는 이 값으로 같은 대화 세션을 이어 보내고, 남은 맥락 정책을 화면에 표시할 수 있다.
 */
public record CharacterTalkStreamMetadata(
        String requestId,
        String sessionId,
        boolean newSession,
        LocalDateTime expiresAt,
        int historyWindowTurns,
        int memorySearchTopK,
        int memoryHitCount
) {
}
