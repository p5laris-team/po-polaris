package p5laris.mission.domain.infrastructure.grpc;

/**
 * ai 모듈이 생성한 자율 미션 후보 결과다.
 *
 * mission 모듈은 gRPC 응답 객체를 직접 들고 다니지 않고,
 * 저장에 필요한 값만 이 record로 받아 사용한다.
 */
public record AiMissionTextResult(
        Long aiGenerationId,
        String title,
        String description,
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse,
        String category,
        String difficulty,
        boolean fallbackUsed,
        String requestId
) {
}
