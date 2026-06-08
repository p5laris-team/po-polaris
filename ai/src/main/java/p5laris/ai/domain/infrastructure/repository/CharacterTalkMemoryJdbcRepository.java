package p5laris.ai.domain.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import p5laris.ai.domain.application.memory.CharacterTalkMemoryHit;
import p5laris.ai.domain.application.memory.EmbeddingVectorUtils;
import p5laris.ai.domain.domain.enums.CharacterTalkMemoryType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 별친구 대화 장기 기억의 pgvector 저장/검색 repository다.
 *
 * Hibernate vector 매핑을 전역으로 늘리지 않고, vector SQL은 이 클래스 안에만 가둔다.
 */
@Repository
@RequiredArgsConstructor
public class CharacterTalkMemoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public void upsertSessionSummary(
            Long userId,
            Long characterId,
            Long sourceSessionId,
            String summary,
            String model,
            int dimension,
            List<Float> normalizedVector
    ) {
        jdbcTemplate.update("""
                        INSERT INTO character_talk_memories (
                            user_id,
                            character_id,
                            source_session_id,
                            memory_type,
                            summary,
                            embedding_model,
                            embedding_dimension,
                            embedding,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (source_session_id, memory_type)
                        DO UPDATE SET
                            summary = EXCLUDED.summary,
                            embedding_model = EXCLUDED.embedding_model,
                            embedding_dimension = EXCLUDED.embedding_dimension,
                            embedding = EXCLUDED.embedding,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                userId,
                characterId,
                sourceSessionId,
                CharacterTalkMemoryType.SESSION_SUMMARY.name(),
                summary,
                model,
                dimension,
                EmbeddingVectorUtils.toPgVectorLiteral(normalizedVector)
        );
    }

    public List<CharacterTalkMemoryHit> searchSimilar(
            Long userId,
            Long characterId,
            String model,
            int dimension,
            List<Float> normalizedQueryVector,
            int topK,
            double similarityThreshold
    ) {
        if (topK <= 0) {
            return List.of();
        }

        double maxDistance = 1.0d - Math.min(1.0d, Math.max(0.0d, similarityThreshold));
        List<CharacterTalkMemoryHit> hits = jdbcTemplate.query("""
                        SELECT id, summary, distance, created_at
                        FROM (
                            SELECT
                                id,
                                summary,
                                created_at,
                                embedding <=> CAST(? AS vector) AS distance
                            FROM character_talk_memories
                            WHERE user_id = ?
                              AND character_id = ?
                              AND memory_type = ?
                              AND embedding_model = ?
                              AND embedding_dimension = ?
                              AND embedding IS NOT NULL
                        ) memories
                        WHERE distance <= ?
                        ORDER BY distance ASC, created_at DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> toHit(rs),
                EmbeddingVectorUtils.toPgVectorLiteral(normalizedQueryVector),
                userId,
                characterId,
                CharacterTalkMemoryType.SESSION_SUMMARY.name(),
                model,
                dimension,
                maxDistance,
                Math.max(1, topK)
        );
        markUsed(hits);
        return hits;
    }

    private CharacterTalkMemoryHit toHit(ResultSet rs) throws SQLException {
        return new CharacterTalkMemoryHit(
                rs.getLong("id"),
                rs.getString("summary"),
                rs.getDouble("distance"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private void markUsed(List<CharacterTalkMemoryHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (CharacterTalkMemoryHit hit : hits) {
            jdbcTemplate.update("""
                            UPDATE character_talk_memories
                            SET last_used_at = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """,
                    now,
                    hit.id()
            );
        }
    }
}
