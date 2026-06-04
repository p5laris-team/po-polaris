package p5laris.mission.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 사용자 기억 RAG 검색 설정이다.
 *
 * embedding model/dimension은 ai 모듈 설정과 mission DB vector 컬럼이 모두 같은 값을 써야 한다.
 */
@Component
@ConfigurationProperties(prefix = "mission.rag")
public class MissionRagProperties {

    private boolean enabled;
    private String embeddingModel;
    private int embeddingDimension;
    private int topK;
    private double similarityThreshold;
    private boolean fallbackToRecentMemory;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmbeddingModel() {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return "gemini-embedding-001";
        }
        return embeddingModel.trim();
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimension() {
        return Math.max(1, embeddingDimension);
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public int getTopK() {
        return Math.max(1, topK);
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        if (similarityThreshold <= 0) {
            return 0.72d;
        }
        return Math.min(1.0d, similarityThreshold);
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public boolean isFallbackToRecentMemory() {
        return fallbackToRecentMemory;
    }

    public void setFallbackToRecentMemory(boolean fallbackToRecentMemory) {
        this.fallbackToRecentMemory = fallbackToRecentMemory;
    }
}
