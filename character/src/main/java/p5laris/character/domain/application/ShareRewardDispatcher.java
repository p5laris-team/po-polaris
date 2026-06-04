package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;
import p5laris.character.domain.infrastructure.grpc.ShareRewardWalletClient;

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

    private final CharacterOutboxEventRepository characterOutboxEventRepository;
    private final ShareLogRepository shareLogRepository;
    private final ShareRewardWalletClient shareRewardWalletClient;
    private final NotificationPushClient notificationPushClient;
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

    public ShareRewardWalletClient.WalletRewardResult dispatchNow(Long outboxId) {
        RewardDispatchCommand command = claim(outboxId, true)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
        return dispatchClaimed(command);
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
                requestRewardCompletedNotification(command.get());
                succeededCount++;
            } catch (CharacterException e) {
                log.warn("공유 보상 outbox 발행에 실패했습니다. outboxId={}, shareLogId={}, errorCode={}",
                        command.get().outboxId(), command.get().shareLogId(), e.getErrorCode().getCode());
            }
        }
        return succeededCount;
    }

    private void requestRewardCompletedNotification(RewardDispatchCommand command) {
        try {
            notificationPushClient.sendShareRewardCompletedNotification(
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

    private ShareRewardWalletClient.WalletRewardResult dispatchClaimed(RewardDispatchCommand command) {
        try {
            ShareRewardWalletClient.WalletRewardResult result = shareRewardWalletClient.earnShareReward(
                    command.userId(),
                    command.shareLogId(),
                    command.rewardStarPiece(),
                    command.idempotencyKey()
            );
            markSucceeded(command);

            meterRegistry.counter("outbox.events.processed",
                    "status", "SUCCESS",
                    "aggregate_type", AGGREGATE_TYPE_SHARE_LOG
            ).increment();

            return result;
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

    private void markSucceeded(RewardDispatchCommand command) {
        transactionTemplate.executeWithoutResult(status -> {
            ShareLog shareLog = shareLogRepository.findByIdForUpdate(command.shareLogId())
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
            CharacterOutboxEvent outbox = characterOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));

            if (!shareLog.isRewardPaid()) {
                shareLog.markRewardPaid();
            }
            outbox.markSucceeded(LocalDateTime.now(clock));
        });
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
