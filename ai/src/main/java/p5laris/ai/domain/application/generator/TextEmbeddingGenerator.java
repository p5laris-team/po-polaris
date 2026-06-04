package p5laris.ai.domain.application.generator;

import java.util.List;

/**
 * 외부 embedding provider를 application 계층에서 직접 의존하지 않기 위한 포트다.
 */
public interface TextEmbeddingGenerator {

    List<Float> generate(String text);
}
