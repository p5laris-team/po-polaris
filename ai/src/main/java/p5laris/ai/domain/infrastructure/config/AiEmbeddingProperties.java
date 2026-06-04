package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 검색용 embedding provider 설정이다.
 *
 * model/dimension은 mission DB의 vector 컬럼과 맞아야 하므로 운영 env에 명시해 둔다.
 */
@ConfigurationProperties(prefix = "ai.embedding")
public class AiEmbeddingProperties {

    private boolean enabled;
    private String model;
    private int dimension;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimension() {
        return Math.max(1, dimension);
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String resolvedModel() {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        return "gemini-embedding-001";
    }
}
