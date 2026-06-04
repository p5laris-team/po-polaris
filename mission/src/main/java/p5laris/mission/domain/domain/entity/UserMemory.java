package p5laris.mission.domain.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;

/**
 * 사용자의 완료 답변과 피드백에서 추출한 개인화 기억이다.
 *
 * 아직 vector DB를 붙이지 않았으므로 여기서는 RAG 원천 데이터를 안정적으로 쌓는 역할만 맡는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_memories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_memories_source_type_id_memory_type",
                columnNames = {"source_type", "source_id", "memory_type"}
        )
)
public class UserMemory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private UserMemorySourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", nullable = false, length = 50)
    private UserMemoryType memoryType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode metadataJson;

    @Column(nullable = false)
    private int importance;

    public static UserMemory create(
            Long userId,
            UserMemorySourceType sourceType,
            Long sourceId,
            UserMemoryType memoryType,
            String content,
            JsonNode metadataJson,
            int importance
    ) {
        UserMemory memory = new UserMemory();
        memory.userId = userId;
        memory.sourceType = sourceType;
        memory.sourceId = sourceId;
        memory.memoryType = memoryType;
        memory.content = content;
        memory.metadataJson = metadataJson;
        memory.importance = importance;
        return memory;
    }

    public void update(String content, JsonNode metadataJson, int importance) {
        this.content = content;
        this.metadataJson = metadataJson;
        this.importance = importance;
    }
}
