package p5laris.ai.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingResult;
import p5laris.ai.domain.application.generator.TextEmbeddingGenerator;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;

import java.util.List;

/**
 * text embedding 생성 use case다.
 *
 * mission 모듈은 이 결과를 user_memory_embeddings에 저장하고, AI 서비스는 외부 provider 호출만 책임진다.
 */
@Service
@RequiredArgsConstructor
public class AiTextEmbeddingService {

    private static final int TEXT_MAX_LENGTH = 2_000;

    private final TextEmbeddingGenerator textEmbeddingGenerator;
    private final AiEmbeddingProperties aiEmbeddingProperties;

    public TextEmbeddingResult generateTextEmbedding(TextEmbeddingCommand command) {
        validate(command);
        if (!aiEmbeddingProperties.isEnabled()) {
            throw new AiException(AiErrorCode.AI_EMBEDDING_FAILED);
        }

        String model = resolveModel(command.model());
        int dimension = resolveDimension(command.dimension());
        validatePolicy(model, dimension);

        try {
            List<Float> values = textEmbeddingGenerator.generate(command.text().strip());
            if (values.size() != dimension) {
                throw new AiException(AiErrorCode.AI_EMBEDDING_FAILED);
            }
            return new TextEmbeddingResult(model, dimension, values, command.requestId());
        } catch (FallbackRequiredException e) {
            throw new AiException(AiErrorCode.AI_EMBEDDING_FAILED);
        }
    }

    private void validate(TextEmbeddingCommand command) {
        if (command == null
                || command.userId() == null
                || command.text() == null
                || command.text().isBlank()
                || command.text().strip().length() > TEXT_MAX_LENGTH
                || command.requestId() == null
                || command.requestId().isBlank()) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }

    private String resolveModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        return aiEmbeddingProperties.resolvedModel();
    }

    private int resolveDimension(int requestedDimension) {
        if (requestedDimension > 0) {
            return requestedDimension;
        }
        return aiEmbeddingProperties.getDimension();
    }

    private void validatePolicy(String model, int dimension) {
        if (!aiEmbeddingProperties.resolvedModel().equals(model)
                || aiEmbeddingProperties.getDimension() != dimension) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }
}
