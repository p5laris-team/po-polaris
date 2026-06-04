package p5laris.ai.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;

import java.time.LocalDateTime;

/**
 * AI가 미션 문구를 생성하거나 fallback 문구를 사용한 결과를 저장하는 엔티티다.
 *
 * mission 모듈은 나중에 이 id를 user_missions.ai_generation_id에 연결해
 * "이 미션 문구가 어떤 AI/fallback 흐름으로 만들어졌는지" 추적할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_mission_generations")
public class AiMissionGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "prompt_template_id")
    private Long promptTemplateId;

    @Column(name = "request_id", nullable = false, length = 120)
    private String requestId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_context_json", nullable = false, columnDefinition = "jsonb")
    private String requestContextJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "selected_template_id")
    private Long selectedTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiGenerationStatus status;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 50)
    private AiErrorType errorType;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // JPA Entity를 외부에서 마음대로 조립하지 않도록 정적 팩토리로 생성 경로를 제한한다.
    public static AiMissionGeneration create(
            Long userId,
            Long characterId,
            Long promptTemplateId,
            String requestId,
            String requestHash,
            String requestContextJson,
            String responseJson,
            Long selectedTemplateId,
            AiGenerationStatus status,
            boolean fallbackUsed,
            String model,
            AiErrorType errorType
    ) {
        AiMissionGeneration generation = new AiMissionGeneration();
        generation.userId = userId;
        generation.characterId = characterId;
        generation.promptTemplateId = promptTemplateId;
        generation.requestId = requestId;
        generation.requestHash = requestHash;
        generation.requestContextJson = requestContextJson;
        generation.responseJson = responseJson;
        generation.selectedTemplateId = selectedTemplateId;
        generation.status = status;
        generation.fallbackUsed = fallbackUsed;
        generation.model = model;
        generation.errorType = errorType;
        return generation;
    }
}
