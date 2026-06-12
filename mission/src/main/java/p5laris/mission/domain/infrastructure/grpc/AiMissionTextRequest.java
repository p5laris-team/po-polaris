package p5laris.mission.domain.infrastructure.grpc;

/**
 * mission 모듈이 ai 모듈에 자율 미션 후보 생성을 요청할 때 필요한 값 묶음이다.
 *
 * requestId는 AI 생성 요청의 멱등 기준이므로, 호출할 때마다 새 UUID를 만들지 않고
 * 같은 미션 후보 생성 시도라면 같은 값이 나오도록 MissionService에서 생성한다.
 */
public record AiMissionTextRequest(
        Long userId,
        Long characterId,
        String characterType,
        String characterName,
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
