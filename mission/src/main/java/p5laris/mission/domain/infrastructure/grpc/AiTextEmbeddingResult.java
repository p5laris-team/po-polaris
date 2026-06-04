package p5laris.mission.domain.infrastructure.grpc;

import java.util.List;

public record AiTextEmbeddingResult(
        String model,
        int dimension,
        List<Float> values,
        String requestId
) {
}
