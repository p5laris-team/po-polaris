package p5laris.mission.domain.infrastructure.grpc;

public record AiTextEmbeddingRequest(
        Long userId,
        String text,
        String model,
        int dimension,
        String requestId
) {
}
