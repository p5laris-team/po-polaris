package p5laris.mission.domain.application;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;
import p5laris.mission.domain.infrastructure.grpc.NotificationPushClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * mission_outbox_events에 쌓인 미션 보상 지급 요청을 실제 wallet 모듈로 발송한다.
 *
 * 미션 완료 트랜잭션은 outbox 저장까지만 책임지고, 이 클래스가 gRPC 호출과 성공/실패 상태 변경을 담당한다.
 * 성공하면 user_missions.idempotency_key에 같은 멱등키를 기록해 이후 같은 완료 요청이 와도 중복 지급하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionRewardDispatcher {

    private final MissionOutboxEventRepository missionOutboxEventRepository;
    private final UserMissionRepository userMissionRepository;
    private final WalletRewardClient walletRewardClient;
    private final NotificationPushClient notificationPushClient;
    private final MissionRewardBackoffPolicy missionRewardBackoffPolicy;
    private final MissionRewardOutboxProperties missionRewardOutboxProperties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", missionOutboxEventRepository,
                repo -> repo.countByStatus(MissionOutboxEventStatus.PENDING));
    }

    public WalletRewardResult dispatchNow(Long outboxId) {
        RewardDispatchCommand command = claim(outboxId, true)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));

        return dispatchClaimed(command, false);
    }

    public int dispatchDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> outboxIds = missionOutboxEventRepository.findDispatchableIds(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                MissionOutboxEventStatus.PROCESSING,
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
                dispatchClaimed(command.get(), true);
                succeededCount++;
            } catch (MissionException e) {
                log.warn("미션 보상 outbox 발송 실패. outboxId={}, missionId={}, errorCode={}",
                        command.get().outboxId(), command.get().missionId(), e.getErrorCode().getCode());
            }
        }
        return succeededCount;
    }

    /*
     * outbox row를 짧은 트랜잭션으로 먼저 PROCESSING 처리한다.
     * 이렇게 해야 여러 서버 인스턴스에서 스케줄러가 동시에 떠도 같은 row를 동시에 발송하는 일을 줄일 수 있다.
     */
    private Optional<RewardDispatchCommand> claim(Long outboxId, boolean immediateDispatch) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElse(null);

            if (outbox == null || !isMissionRewardEvent(outbox) || !canClaim(outbox, now, immediateDispatch)) {
                return Optional.empty();
            }

            outbox.markProcessing(now.plusSeconds(missionRewardOutboxProperties.getProcessingTimeoutSeconds()));
            return Optional.of(toCommand(outbox));
        });
    }

    private boolean isMissionRewardEvent(MissionOutboxEvent outbox) {
        return MissionOutboxEvent.AGGREGATE_TYPE_MISSION.equals(outbox.getAggregateType())
                && MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED.equals(outbox.getEventType());
    }

    /*
     * 즉시 발송은 사용자가 방금 완료한 보상이라 nextAttemptAt 대기 없이 PENDING row를 바로 집는다.
     * 스케줄러 발송은 backoff 시간이 지난 row만 집어 과도한 재시도를 막는다.
     */
    private boolean canClaim(MissionOutboxEvent outbox, LocalDateTime now, boolean immediateDispatch) {
        if (!immediateDispatch) {
            return outbox.canBeClaimed(now);
        }

        if (outbox.getStatus() == MissionOutboxEventStatus.PENDING) {
            return true;
        }
        return outbox.getStatus() == MissionOutboxEventStatus.PROCESSING
                && !outbox.getNextAttemptAt().isAfter(now);
    }

    private RewardDispatchCommand toCommand(MissionOutboxEvent outbox) {
        try {
            MissionRewardRequestedPayload payload = objectMapper.treeToValue(
                    outbox.getPayload(),
                    MissionRewardRequestedPayload.class
            );
            // aggregate_id를 missionId의 기준값으로 보고, payload와 어긋나면 잘못된 이벤트로 판단한다.
            Long missionId = outbox.getAggregateId();
            if (payload.missionId() != null && !payload.missionId().equals(missionId)) {
                throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
            }
            if (missionId == null || payload.userId() == null
                    || payload.rewardStarPiece() == null || payload.rewardStarPiece() <= 0) {
                throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
            }

            return new RewardDispatchCommand(
                    outbox.getId(),
                    missionId,
                    payload.userId(),
                    payload.rewardStarPiece(),
                    outbox.getIdempotencyKey()
            );
        } catch (Exception e) {
            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        }
    }

    /*
     * gRPC 호출은 mission DB 트랜잭션 밖에서 수행한다.
     * wallet 호출 성공/실패 결과만 다시 짧은 트랜잭션으로 반영해 DB lock 보유 시간을 줄인다.
     */
    private WalletRewardResult dispatchClaimed(RewardDispatchCommand command, boolean notifyRewardRecovered) {
        try {
            WalletRewardResult result = walletRewardClient.earnMissionReward(
                    command.userId(),
                    command.missionId(),
                    command.rewardStarPiece(),
                    command.idempotencyKey()
            );
            markSucceeded(command);
            if (notifyRewardRecovered) {
                notifyRewardRecovered(command);
            }

            meterRegistry.counter("outbox.events.processed",
                    "status", "SUCCESS",
                    "aggregate_type", "MISSION"
            ).increment();

            return result;
        } catch (MissionException e) {
            markFailed(command, e.getMessage());

            meterRegistry.counter("outbox.events.processed",
                    "status", "FAILURE",
                    "aggregate_type", "MISSION"
            ).increment();

            throw e;
        } catch (Exception e) {
            markFailed(command, e.getMessage());

            meterRegistry.counter("outbox.events.processed",
                    "status", "FAILURE",
                    "aggregate_type", "MISSION"
            ).increment();

            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        }
    }

    private void markSucceeded(RewardDispatchCommand command) {
        transactionTemplate.executeWithoutResult(status -> {
            UserMission mission = userMissionRepository.findByIdAndUserIdForUpdate(command.missionId(), command.userId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));

            if (!mission.isCompleted()) {
                throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
            }
            if (!mission.isRewardPaid()) {
                mission.recordRewardPaid(command.idempotencyKey());
            }
            outbox.markSucceeded(LocalDateTime.now(clock));
        });
    }

    private void notifyRewardRecovered(RewardDispatchCommand command) {
        try {
            notificationPushClient.sendMissionRewardRecoveredNotification(
                    command.userId(),
                    command.missionId(),
                    command.rewardStarPiece()
            );
        } catch (Exception e) {
            log.warn("미션 보상 재처리 완료 알림 전송 실패. userId={}, missionId={}, outboxId={}",
                    command.userId(), command.missionId(), command.outboxId(), e);
        }
    }

    /*
     * 실패한 outbox는 attempt_count를 증가시키고 다음 재시도 시각을 backoff 정책으로 미룬다.
     * maxAttempts에 도달하면 FAILED로 남겨 운영자가 원인을 확인할 수 있게 한다.
     */
    private void markFailed(RewardDispatchCommand command, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));
            LocalDateTime now = LocalDateTime.now(clock);
            int nextAttemptCount = outbox.getAttemptCount() + 1;
            outbox.recordFailure(
                    errorMessage,
                    missionRewardBackoffPolicy.nextAttemptAt(now, nextAttemptCount),
                    missionRewardOutboxProperties.getMaxAttempts()
            );
        });
    }

    private record RewardDispatchCommand(
            Long outboxId,
            Long missionId,
            Long userId,
            int rewardStarPiece,
            String idempotencyKey
    ) {

    }
}
