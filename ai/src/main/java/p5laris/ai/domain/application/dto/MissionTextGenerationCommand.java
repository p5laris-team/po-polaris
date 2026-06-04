package p5laris.ai.domain.application.dto;

/**
 * 자율 미션 후보 생성을 위해 application 계층으로 전달되는 입력 값이다.
 *
 * seed 미션 정보, 캐릭터 타입, fallback 문구, 개인화 context를 한 번에 묶는다.
 */
public record MissionTextGenerationCommand(
        Long userId,
        Long characterId,
        String characterType,
        Long missionTemplateId,
        String baseTitle,
        String baseDescription,
        String category,
        String difficulty,
        String fallbackCharacterMessage,
        String fallbackQuestion,
        String fallbackCompletionResponse,
        String onboardingContextJson,
        String recentMissionContextJson,
        String requestId
) {
}
