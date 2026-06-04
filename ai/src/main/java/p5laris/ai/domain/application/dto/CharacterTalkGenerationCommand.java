package p5laris.ai.domain.application.dto;

/**
 * 별친구 대화 생성 요청 command다.
 *
 * 사용자 메시지는 저장하지 않고 provider 호출과 즉시 응답 생성에만 사용한다.
 */
public record CharacterTalkGenerationCommand(
        Long userId,
        Long characterId,
        String characterType,
        String characterName,
        String userMessage,
        String interactionType,
        String characterContextJson,
        String requestId
) {
}
