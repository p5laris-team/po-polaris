package p5laris.gateway.domain.character.api.dto;

/**
 * 별친구에게 말을 걸 때 gateway가 받는 요청이다.
 *
 * 대화 원문은 저장하지 않고, AI 응답 생성에만 사용한다.
 */
public record CharacterTalkStreamRequest(
        String message,
        String interactionType
) {
}
