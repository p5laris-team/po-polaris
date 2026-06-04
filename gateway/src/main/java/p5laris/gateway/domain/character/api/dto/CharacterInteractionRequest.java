package p5laris.gateway.domain.character.api.dto;

/**
 * 별친구 상호작용 요청이다.
 *
 * interactionType을 비우면 character 모듈에서 TAP으로 처리한다.
 */
public record CharacterInteractionRequest(
        String interactionType
) {
}
