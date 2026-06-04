package p5laris.mission.domain.application.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.infrastructure.config.MissionMemoryEmbeddingProperties;
import p5laris.mission.domain.infrastructure.config.MissionRagProperties;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingClient;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingRequest;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingResult;
import p5laris.mission.domain.infrastructure.repository.UserMemoryEmbeddingJdbcRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PENDING user_memory_embeddings row를 외부 AI embedding 호출로 채운다.
 *
 * row claim과 상태 변경만 짧은 트랜잭션으로 처리하고, gRPC 호출은 트랜잭션 밖에서 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryEmbeddingDispatcher {

    private static final String REQUEST_ID_PREFIX = "USER_MEMORY_EMBEDDING:";

    private final UserMemoryEmbeddingJdbcRepository userMemoryEmbeddingJdbcRepository;
    private final AiTextEmbeddingClient aiTextEmbeddingClient;
    private final MissionMemoryEmbeddingBackoffPolicy backoffPolicy;
    private final MissionMemoryEmbeddingProperties embeddingProperties;
    private final MissionRagProperties ragProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public int dispatchDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> embeddingIds = userMemoryEmbeddingJdbcRepository.findDispatchableIds(now, batchSize);

        int succeededCount = 0;
        for (Long embeddingId : embeddingIds) {
            Optional<UserMemoryEmbeddingJob> job = claim(embeddingId);
            if (job.isEmpty()) {
                continue;
            }

            if (dispatchClaimed(job.get())) {
                succeededCount++;
            }
        }
        return succeededCount;
    }

    private Optional<UserMemoryEmbeddingJob> claim(Long embeddingId) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (!userMemoryEmbeddingJdbcRepository.canClaim(embeddingId, now)) {
                return Optional.empty();
            }

            Optional<UserMemoryEmbeddingJob> job = userMemoryEmbeddingJdbcRepository.findJobForUpdate(embeddingId);
            job.ifPresent(value -> userMemoryEmbeddingJdbcRepository.markProcessing(
                    value.id(),
                    now.plusSeconds(embeddingProperties.getProcessingTimeoutSeconds())
            ));
            return job;
        });
    }

    private boolean dispatchClaimed(UserMemoryEmbeddingJob job) {
        try {
            Optional<AiTextEmbeddingResult> generated = aiTextEmbeddingClient.generateTextEmbedding(new AiTextEmbeddingRequest(
                    job.userId(),
                    job.content(),
                    ragProperties.getEmbeddingModel(),
                    ragProperties.getEmbeddingDimension(),
                    REQUEST_ID_PREFIX + job.userMemoryId()
            ));

            if (generated.isEmpty()) {
                markFailed(job, "AI embedding 응답이 비어 있습니다.");
                return false;
            }

            AiTextEmbeddingResult result = generated.get();
            if (!ragProperties.getEmbeddingModel().equals(result.model())
                    || ragProperties.getEmbeddingDimension() != result.dimension()) {
                markFailed(job, "AI embedding model/dimension이 mission 정책과 다릅니다.");
                return false;
            }

            List<Float> normalized = EmbeddingVectorUtils.normalize(
                    result.values(),
                    ragProperties.getEmbeddingDimension()
            );
            markSucceeded(job, normalized);
            return true;
        } catch (Exception e) {
            log.warn("사용자 기억 embedding 생성 실패. embeddingId={}, userMemoryId={}",
                    job.id(), job.userMemoryId(), e);
            markFailed(job, e.getMessage());
            return false;
        }
    }

    private void markSucceeded(UserMemoryEmbeddingJob job, List<Float> normalizedVector) {
        transactionTemplate.executeWithoutResult(status -> userMemoryEmbeddingJdbcRepository.markSucceeded(
                job.id(),
                normalizedVector,
                LocalDateTime.now(clock)
        ));
    }

    private void markFailed(UserMemoryEmbeddingJob job, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            int nextAttemptCount = job.attemptCount() + 1;
            userMemoryEmbeddingJdbcRepository.recordFailure(
                    job.id(),
                    errorMessage,
                    backoffPolicy.nextAttemptAt(now, nextAttemptCount),
                    embeddingProperties.getMaxAttempts()
            );
        });
    }
}
