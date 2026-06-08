package p5laris.ai.domain.application.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector에 저장할 embedding vector를 검증, 정규화, 문자열 리터럴로 변환한다.
 *
 * Gemini embedding 차원을 줄여 사용할 때는 cosine 검색 품질을 위해 저장 전에 정규화한다.
 */
public final class EmbeddingVectorUtils {

    private EmbeddingVectorUtils() {
    }

    public static List<Float> normalize(List<Float> values, int expectedDimension) {
        if (values == null || values.size() != expectedDimension) {
            throw new IllegalArgumentException("embedding dimension mismatch");
        }

        double normSquared = 0.0d;
        for (Float value : values) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding contains invalid value");
            }
            normSquared += value * value;
        }

        double norm = Math.sqrt(normSquared);
        if (norm == 0.0d) {
            throw new IllegalArgumentException("embedding norm is zero");
        }

        List<Float> normalized = new ArrayList<>(values.size());
        for (Float value : values) {
            normalized.add((float) (value / norm));
        }
        return normalized;
    }

    public static String toPgVectorLiteral(List<Float> values) {
        StringBuilder builder = new StringBuilder(values.size() * 8);
        builder.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values.get(index)));
        }
        builder.append(']');
        return builder.toString();
    }
}
