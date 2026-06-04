package p5laris.ai.domain.application.dto;

import java.util.List;

/**
 * 외부 embedding provider가 반환한 벡터 결과다.
 *
 * 벡터 정규화와 저장은 호출자인 mission 모듈이 담당한다.
 */
public record TextEmbeddingResult(
        String model,
        int dimension,
        List<Float> values,
        String requestId
) {
}
