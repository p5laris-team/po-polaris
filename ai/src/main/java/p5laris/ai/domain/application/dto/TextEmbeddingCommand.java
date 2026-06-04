package p5laris.ai.domain.application.dto;

/**
 * mission 모듈이 사용자 기억 RAG 검색에 사용할 text embedding 생성을 요청할 때의 입력값이다.
 */
public record TextEmbeddingCommand(
        Long userId,
        String text,
        String model,
        int dimension,
        String requestId
) {
}
