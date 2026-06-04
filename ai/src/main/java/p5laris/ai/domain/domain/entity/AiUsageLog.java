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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiUsageStatus;

import java.time.LocalDateTime;

/**
 * AI provider 사용량과 장애율을 분석하기 위한 로그 엔티티다.
 *
 * 현재는 provider 응답의 token metadata를 직접 추출하지 않으므로 token은 0으로 저장하고,
 * latency/status/errorType을 우선 남겨 fallback 비율과 장애 원인을 분석한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_usage_logs")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "request_id", nullable = false, length = 120)
    private String requestId;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiUsageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 50)
    private AiErrorType errorType;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // rule-based generator는 token 사용량이 없으므로 0으로 저장한다.
    public static AiUsageLog create(
            Long userId,
            String requestId,
            String model,
            int latencyMs,
            AiUsageStatus status,
            AiErrorType errorType
    ) {
        AiUsageLog usageLog = new AiUsageLog();
        usageLog.userId = userId;
        usageLog.requestId = requestId;
        usageLog.model = model;
        usageLog.promptTokens = 0;
        usageLog.completionTokens = 0;
        usageLog.totalTokens = 0;
        usageLog.latencyMs = latencyMs;
        usageLog.status = status;
        usageLog.errorType = errorType;
        return usageLog;
    }
}
