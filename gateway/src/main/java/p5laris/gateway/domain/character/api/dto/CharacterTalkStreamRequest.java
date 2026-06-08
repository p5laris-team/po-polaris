package p5laris.gateway.domain.character.api.dto;

/**
 * 별친구에게 말을 걸 때 gateway가 받는 요청이다.
 *
 * sessionId가 있으면 같은 별친구 대화 세션을 이어가고, 없으면 서버가 활성 세션을 찾거나 새로 만든다.
 */
public record CharacterTalkStreamRequest(
        String message,
        String interactionType,
        String sessionId
) {
}
