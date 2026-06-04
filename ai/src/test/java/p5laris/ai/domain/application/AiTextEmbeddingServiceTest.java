package p5laris.ai.domain.application;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.generator.TextEmbeddingGenerator;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTextEmbeddingServiceTest {

    @Test
    void text_embedding을_생성한다() {
        AiTextEmbeddingService service = new AiTextEmbeddingService(
                text -> List.of(0.1f, 0.2f, 0.3f),
                embeddingProperties(true, 3)
        );

        var result = service.generateTextEmbedding(new TextEmbeddingCommand(
                1001L,
                "밤에는 조용한 미션이 좋아요",
                "gemini-embedding-001",
                3,
                "request-1"
        ));

        assertThat(result.model()).isEqualTo("gemini-embedding-001");
        assertThat(result.dimension()).isEqualTo(3);
        assertThat(result.values()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void provider가_비활성화되어_있으면_embedding_생성을_거절한다() {
        AiTextEmbeddingService service = new AiTextEmbeddingService(
                unusedGenerator(),
                embeddingProperties(false, 3)
        );

        assertThatThrownBy(() -> service.generateTextEmbedding(new TextEmbeddingCommand(
                1001L,
                "테스트",
                "gemini-embedding-001",
                3,
                "request-1"
        )))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_EMBEDDING_FAILED);
    }

    @Test
    void 요청_dimension과_응답_dimension이_다르면_실패한다() {
        AiTextEmbeddingService service = new AiTextEmbeddingService(
                text -> List.of(0.1f, 0.2f),
                embeddingProperties(true, 3)
        );

        assertThatThrownBy(() -> service.generateTextEmbedding(new TextEmbeddingCommand(
                1001L,
                "테스트",
                "gemini-embedding-001",
                3,
                "request-1"
        )))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_EMBEDDING_FAILED);
    }

    private AiEmbeddingProperties embeddingProperties(boolean enabled, int dimension) {
        AiEmbeddingProperties properties = new AiEmbeddingProperties();
        properties.setEnabled(enabled);
        properties.setModel("gemini-embedding-001");
        properties.setDimension(dimension);
        return properties;
    }

    private TextEmbeddingGenerator unusedGenerator() {
        return text -> {
            throw new AssertionError("비활성화 상태에서는 provider를 호출하지 않아야 한다.");
        };
    }
}
