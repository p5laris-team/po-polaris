package p5laris.mission.domain.application.personalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import p5laris.mission.domain.application.memory.UserMemoryRagHit;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;
import p5laris.mission.domain.infrastructure.config.MissionRagProperties;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingClient;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingResult;
import p5laris.mission.domain.infrastructure.repository.UserMemoryEmbeddingJdbcRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionRagContextServiceTest {

    private final AiTextEmbeddingClient aiTextEmbeddingClient = mock(AiTextEmbeddingClient.class);
    private final UserMemoryEmbeddingJdbcRepository userMemoryEmbeddingJdbcRepository = mock(UserMemoryEmbeddingJdbcRepository.class);
    private final MissionRagProperties missionRagProperties = ragProperties();
    private final MissionRagContextService service = new MissionRagContextService(
            aiTextEmbeddingClient,
            userMemoryEmbeddingJdbcRepository,
            missionRagProperties,
            new ObjectMapper()
    );

    @Test
    void RAG_검색_결과를_recentMissionContext에_추가한다() {
        when(aiTextEmbeddingClient.generateTextEmbedding(any()))
                .thenReturn(Optional.of(new AiTextEmbeddingResult(
                        "gemini-embedding-001",
                        3,
                        List.of(3.0f, 4.0f, 0.0f),
                        "request"
                )));
        when(userMemoryEmbeddingJdbcRepository.searchSimilar(any(), any(), anyInt(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new UserMemoryRagHit(
                        1L,
                        UserMemoryType.MISSION_REJECTION,
                        UserMemorySourceType.MISSION_FEEDBACK,
                        "거절 이유: 밤에는 운동이 부담스러웠어 / 코드: TOO_HARD",
                        new ObjectMapper().createObjectNode().put("reasonCode", "TOO_HARD"),
                        75,
                        LocalDateTime.of(2026, 5, 29, 21, 0),
                        0.12d
                )));

        String enriched = service.enrich(
                1001L,
                new MissionRagQuery(1001L, 10L, "가벼운 스트레칭", "3분 동안 몸을 풀어보세요.", "BODY_CARE", "NORMAL"),
                "{\"memoryPolicy\":{\"available\":false},\"userMemories\":[]}"
        );

        assertThat(enriched)
                .contains("\"ragSelection\":\"RAG_COSINE_TOP_K_WITH_RECENT_FALLBACK\"")
                .contains("\"ragSimilarityThreshold\":0.72")
                .contains("\"ragMemories\"")
                .contains("\"type\":\"MISSION_REJECTION\"")
                .contains("\"reasonCode\":\"TOO_HARD\"");
    }

    @Test
    void RAG_검색_결과가_없으면_빈_ragMemories를_명시한다() {
        when(aiTextEmbeddingClient.generateTextEmbedding(any()))
                .thenReturn(Optional.of(new AiTextEmbeddingResult(
                        "gemini-embedding-001",
                        3,
                        List.of(3.0f, 4.0f, 0.0f),
                        "request"
                )));
        when(userMemoryEmbeddingJdbcRepository.searchSimilar(any(), any(), anyInt(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        String enriched = service.enrich(
                1001L,
                new MissionRagQuery(1001L, 10L, "가벼운 스트레칭", "3분 동안 몸을 풀어보세요.", "BODY_CARE", "NORMAL"),
                "{\"memoryPolicy\":{\"available\":true},\"userMemories\":[{\"content\":\"최근 기억\"}]}"
        );

        assertThat(enriched)
                .contains("\"ragAvailable\":false")
                .contains("\"ragHitCount\":0")
                .contains("\"ragMemories\":[]")
                .contains("\"content\":\"최근 기억\"");
    }

    @Test
    void RAG_검색이_실패하면_기존_context를_그대로_반환한다() {
        when(aiTextEmbeddingClient.generateTextEmbedding(any()))
                .thenReturn(Optional.empty());
        String original = "{\"memoryPolicy\":{\"available\":false},\"userMemories\":[]}";

        String result = service.enrich(
                1001L,
                new MissionRagQuery(1001L, 10L, "가벼운 스트레칭", "3분 동안 몸을 풀어보세요.", "BODY_CARE", "NORMAL"),
                original
        );

        assertThat(result).isEqualTo(original);
    }

    private MissionRagProperties ragProperties() {
        MissionRagProperties properties = new MissionRagProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModel("gemini-embedding-001");
        properties.setEmbeddingDimension(3);
        properties.setTopK(5);
        properties.setFallbackToRecentMemory(true);
        return properties;
    }
}
