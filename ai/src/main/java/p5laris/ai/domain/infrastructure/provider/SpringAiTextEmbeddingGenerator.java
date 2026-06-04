package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.generator.TextEmbeddingGenerator;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI EmbeddingModel을 감싸는 adapter다.
 *
 * 실제 Gemini model/dimension은 application.yaml의 spring.ai.google.genai.embedding 설정에서 제어한다.
 */
@Component
@RequiredArgsConstructor
public class SpringAiTextEmbeddingGenerator implements TextEmbeddingGenerator {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Override
    public List<Float> generate(String text) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new FallbackRequiredException(
                    AiErrorType.PROVIDER_ERROR,
                    "Spring AI EmbeddingModel이 구성되지 않았습니다."
            );
        }

        float[] values = embeddingModel.embed(text);
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
