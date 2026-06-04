package p5laris.ai.domain.application.dto;

/**
 * provider 또는 rule-based generator가 만든 자율 미션 후보다.
 *
 * 이 값은 바로 저장하지 않고 MissionTextValidationPolicy 검증을 통과해야 한다.
 */
public record MissionTextCandidate(
        String title,
        String description,
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse,
        String category,
        String difficulty
) {
}
