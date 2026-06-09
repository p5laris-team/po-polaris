package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.application.event.CharacterNotificationRequestPublisher;
import p5laris.character.domain.application.event.StarPieceEarnRequestedEvent;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareRewardDispatcher {

    public static final String AGGREGATE_TYPE_SHARE_LOG = "SHARE_LOG";
    public static final String EVENT_TYPE_SHARE_REWARD_REQUESTED = "SHARE_REWARD_REQUESTED";
    private static final String STAR_PIECE_EARN_REQUESTED_TOPIC = "star-piece-earn-requested";
    private static final String SHARE_REWARD_REASON = "SHARE_REWARD";
    private static final String SHARE_REF_TYPE = "SHARE";

    private final CharacterOutboxEventRepository characterOutboxEventRepository;
    private final ShareLogRepository shareLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CharacterNotificationRequestPublisher notificationRequestPublisher;
    private final ShareRewardBackoffPolicy shareRewardBackoffPolicy;
    private final ShareRewardOutboxProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", characterOutboxEventRepository,
                repo -> repo.countByStatus(CharacterOutboxEventStatus.PENDING));
    }

    public void dispatchNow(Long outboxId) {
        RewardDispatchCommand command = claim(outboxId, true)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
        dispatchClaimed(command);
    }

    public int dispatchDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> outboxIds = characterOutboxEventRepository.findDispatchableIds(
                EVENT_TYPE_SHARE_REWARD_REQUESTED,
                CharacterOutboxEventStatus.PENDING,
                CharacterOutboxEventStatus.PROCESSING,
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );

        int succeededCount = 0;
        for (Long outboxId : outboxIds) {
            Optional<RewardDispatchCommand> command = claim(outboxId, false);
            if (command.isEmpty()) {
                continue;
            }

            try {
                dispatchClaimed(command.get());
                succeededCount++;
            } catch (CharacterException e) {
                log.warn("공유 보상 outbox Kafka 발행에 실패했습니다. outboxId={}, shareLogId={}, errorCode={}",
                        command.get().outboxId(), command.get().shareLogId(), e.getErrorCode().getCode());
            }
        }
        return succeededCount;
    }

    private void requestRewardCompletedNotification(RewardDispatchCommand command) {
        try {
            notificationRequestPublisher.requestShareRewardCompletedNotification(
                    command.userId(),
                    command.shareLogId(),
                    command.rewardStarPiece()
            );
        } catch (Exception e) {
            log.warn("공유 보상 완료 알림 요청에 실패했습니다. userId={}, shareLogId={}",
                    command.userId(), command.shareLogId(), e);
        }
    }

    private Optional<RewardDispatchCommand> claim(Long outboxId, boolean immediateDispatch) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElse(null);

            if (outbox == null || !isShareRewardEvent(outbox) || !canClaim(outbox, now, immediateDispatch)) {
                return Optional.empty();
            }

            outbox.markProcessing(now.plusSeconds(properties.getProcessingTimeoutSeconds()));
            return Optional.of(toCommand(outbox));
        });
    }

    private boolean isShareRewardEvent(CharacterOutboxEvent outbox) {
        return AGGREGATE_TYPE_SHARE_LOG.equals(outbox.getAggregateType())
                && EVENT_TYPE_SHARE_REWARD_REQUESTED.equals(outbox.getEventType());
    }

    private boolean canClaim(CharacterOutboxEvent outbox, LocalDateTime now, boolean immediateDispatch) {
        if (!immediateDispatch) {
            return outbox.canBeClaimed(now);
        }

        if (outbox.getStatus() == CharacterOutboxEventStatus.PENDING) {
            return true;
        }
        return outbox.getStatus() == CharacterOutboxEventStatus.PROCESSING
                && !outbox.getNextAttemptAt().isAfter(now);
    }

    private RewardDispatchCommand toCommand(CharacterOutboxEvent outbox) {
        try {
            ShareRewardPayload payload = objectMapper.treeToValue(outbox.getPayload(), ShareRewardPayload.class);
            return new RewardDispatchCommand(
                    outbox.getId(),
                    outbox.getAggregateId(),
                    payload.userId(),
                    payload.rewardStarPiece(),
                    outbox.getIdempotencyKey()
            );
        } catch (Exception e) {
            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        }
    }

    private void dispatchClaimed(RewardDispatchCommand command) {
        try {
            StarPieceEarnRequestedEvent event = StarPieceEarnRequestedEvent.builder()
                    .outboxId(command.outboxId())
                    .userId(command.userId())
                    .amount(command.rewardStarPiece())
                    .reason(SHARE_REWARD_REASON)
                    .refType(SHARE_REF_TYPE)
                    .refId(command.shareLogId())
                    .idempotencyKey(command.idempotencyKey())
                    .build();
            kafkaTemplate.send(STAR_PIECE_EARN_REQUESTED_TOPIC, command.idempotencyKey(), event);

            meterRegistry.counter("outbox.events.processed",
                    "status", "PUBLISHED",
                    "aggregate_type", AGGREGATE_TYPE_SHARE_LOG
            ).increment();
        } catch (CharacterException e) {
            markFailed(command, e.getMessage());

            meterRegistry.counter("outbox.events.processed",
                    "status", "FAILURE",
                    "aggregate_type", AGGREGATE_TYPE_SHARE_LOG
            ).increment();

            throw e;
        } catch (Exception e) {
            markFailed(command, e.getMessage());

            meterRegistry.counter("outbox.events.processed",
                    "status", "FAILURE",
                    "aggregate_type", AGGREGATE_TYPE_SHARE_LOG
            ).increment();

            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        }
    }

    public void markSucceeded(Long outboxId, String idempotencyKey) {
        RewardDispatchCommand command = transactionTemplate.execute(status -> {
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
            if (!isShareRewardEvent(outbox) || !outbox.getIdempotencyKey().equals(idempotencyKey)) {
                throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
            }
            return toCommand(outbox);
        });
        boolean newlySucceeded = markSucceeded(command);
        if (newlySucceeded) {
            requestRewardCompletedNotification(command);
        }
    }

    private boolean markSucceeded(RewardDispatchCommand command) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            ShareLog shareLog = shareLogRepository.findByIdForUpdate(command.shareLogId())
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));

            if (shareLog.isRewardPaid() && outbox.getStatus() == CharacterOutboxEventStatus.SUCCEEDED) {
                return false;
            }
            if (!shareLog.isRewardPaid()) {
                shareLog.markRewardPaid();
            }
            outbox.markSucceeded(LocalDateTime.now(clock));
            return true;
        }));
    }

    public void markFailed(Long outboxId, String idempotencyKey, String errorMessage) {
        RewardDispatchCommand command = transactionTemplate.execute(status -> {
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
            if (!isShareRewardEvent(outbox) || !outbox.getIdempotencyKey().equals(idempotencyKey)) {
                throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
            }
            return toCommand(outbox);
        });
        markFailed(command, errorMessage);
    }

    private void markFailed(RewardDispatchCommand command, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
            LocalDateTime now = LocalDateTime.now(clock);
            int nextAttemptCount = outbox.getAttemptCount() + 1;
            outbox.recordFailure(
                    errorMessage,
                    shareRewardBackoffPolicy.nextAttemptAt(now, nextAttemptCount),
                    properties.getMaxAttempts()
            );
        });
    }

    public record ShareRewardPayload(Long userId, int rewardStarPiece) {
    }

    private record RewardDispatchCommand(
            Long outboxId,
            Long shareLogId,
            Long userId,
            int rewardStarPiece,
            String idempotencyKey
    ) {
    }
}
