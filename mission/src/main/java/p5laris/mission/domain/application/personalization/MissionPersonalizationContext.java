package p5laris.mission.domain.application.personalization;

/**
 * AI 미션 생성 요청에 전달할 개인화 context JSON 묶음이다.
 *
 * ai proto가 온보딩 context와 최근 미션 context를 나누어 받기 때문에
 * mission 내부에서도 두 문자열을 분리해 들고 간다.
 */
public record MissionPersonalizationContext(
        String onboardingContextJson,
        String recentMissionContextJson
) {
}
