package p5laris.mission.domain.application.memory;

/**
 * embedding 생성 스케줄러가 외부 AI 호출에 필요한 값만 들고 가는 스냅샷이다.
 */
public record UserMemoryEmbeddingJob(
        Long id,
        Long userMemoryId,
        Long userId,
        String content,
        int attemptCount
) {
}
