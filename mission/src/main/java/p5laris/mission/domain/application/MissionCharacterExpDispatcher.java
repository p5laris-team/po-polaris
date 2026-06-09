package p5laris.mission.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.application.event.MissionCharacterExpRequestedEvent;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;
import p5laris.mission.domain.domain.repository.MissionOutboxEventRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * mission_outbox_events에 쌓인 미션 완료 캐릭터 경험치 지급 요청을 character 모듈로 발송한다.
 *
 * 미션 완료 트랜잭션은 outbox 저장까지만 책임지고, 이 클래스가 gRPC 호출과 outbox 성공/실패 상태 변경을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionCharacterExpDispatcher {

    private static final String EXP_REQUESTED_TOPIC = "mission-character-exp-requested";

    private final MissionOutboxEventRepository missionOutboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MissionRewardBackoffPolicy missionRewardBackoffPolicy;
    private final MissionRewardOutboxProperties missionRewardOutboxProperties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public void dispatchNow(Long outboxId) {
        CharacterExpDispatchCommand command = claim(outboxId, true)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED));

        dispatchClaimed(command);
    }

    public int dispatchDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> outboxIds = missionOutboxEventRepository.findDispatchableIds(
                MissionOutboxEvent.EVENT_TYPE_MISSION_CHARACTER_EXP_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                MissionOutboxEventStatus.PROCESSING,
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );

        int succeededCount = 0;
        for (Long outboxId : outboxIds) {
            Optional<CharacterExpDispatchCommand> command = claim(outboxId, false);
            if (command.isEmpty()) {
                continue;
            }

            try {
                dispatchClaimed(command.get());
                succeededCount++;
            } catch (MissionException e) {
                log.warn("미션 캐릭터 경험치 outbox Kafka 발행 실패. outboxId={}, missionId={}, errorCode={}",
                        command.get().outboxId(), command.get().missionId(), e.getErrorCode().getCode());
            }
        }
        return succeededCount;
    }

    private Optional<CharacterExpDispatchCommand> claim(Long outboxId, boolean immediateDispatch) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElse(null);

            if (outbox == null || !isMissionCharacterExpEvent(outbox) || !canClaim(outbox, now, immediateDispatch)) {
                return Optional.empty();
            }

            outbox.markProcessing(now.plusSeconds(missionRewardOutboxProperties.getProcessingTimeoutSeconds()));
            return Optional.of(toCommand(outbox));
        });
    }

    private boolean isMissionCharacterExpEvent(MissionOutboxEvent outbox) {
        return MissionOutboxEvent.AGGREGATE_TYPE_MISSION.equals(outbox.getAggregateType())
                && MissionOutboxEvent.EVENT_TYPE_MISSION_CHARACTER_EXP_REQUESTED.equals(outbox.getEventType());
    }

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

    private CharacterExpDispatchCommand toCommand(MissionOutboxEvent outbox) {
        try {
            MissionCharacterExpRequestedPayload payload = objectMapper.treeToValue(
                    outbox.getPayload(),
                    MissionCharacterExpRequestedPayload.class
            );
            Long missionId = outbox.getAggregateId();
            if (payload.missionId() != null && !payload.missionId().equals(missionId)) {
                throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
            }
            if (missionId == null || payload.userId() == null || payload.characterId() == null
                    || payload.expAmount() == null || payload.expAmount() <= 0) {
                throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
            }

            return new CharacterExpDispatchCommand(
                    outbox.getId(),
                    missionId,
                    payload.userId(),
                    payload.characterId(),
                    payload.difficulty(),
                    payload.expAmount(),
                    outbox.getIdempotencyKey()
            );
        } catch (MissionException e) {
            throw e;
        } catch (Exception e) {
            throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
        }
    }

    private void dispatchClaimed(CharacterExpDispatchCommand command) {
        try {
            MissionCharacterExpRequestedEvent event = MissionCharacterExpRequestedEvent.builder()
                    .outboxId(command.outboxId())
                    .missionId(command.missionId())
                    .userId(command.userId())
                    .characterId(command.characterId())
                    .difficulty(command.difficulty())
                    .expAmount(command.expAmount())
                    .idempotencyKey(command.idempotencyKey())
                    .build();
            kafkaTemplate.send(EXP_REQUESTED_TOPIC, command.idempotencyKey(), event);

            meterRegistry.counter("outbox.events.processed",
                    "status", "PUBLISHED",
                    "aggregate_type", "MISSION"
            ).increment();
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

            throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
        }
    }

    public void markSucceeded(Long outboxId, String idempotencyKey) {
        transactionTemplate.executeWithoutResult(status -> {
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED));
            if (!isMissionCharacterExpEvent(outbox) || !outbox.getIdempotencyKey().equals(idempotencyKey)) {
                throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
            }
            outbox.markSucceeded(LocalDateTime.now(clock));
        });
    }

    public void markFailed(Long outboxId, String idempotencyKey, String errorMessage) {
        CharacterExpDispatchCommand command = transactionTemplate.execute(status -> {
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(outboxId)
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED));
            if (!isMissionCharacterExpEvent(outbox) || !outbox.getIdempotencyKey().equals(idempotencyKey)) {
                throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
            }
            return toCommand(outbox);
        });
        markFailed(command, errorMessage);
    }

    private void markFailed(CharacterExpDispatchCommand command, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            MissionOutboxEvent outbox = missionOutboxEventRepository.findByIdForUpdate(command.outboxId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED));
            LocalDateTime now = LocalDateTime.now(clock);
            int nextAttemptCount = outbox.getAttemptCount() + 1;
            outbox.recordFailure(
                    errorMessage,
                    missionRewardBackoffPolicy.nextAttemptAt(now, nextAttemptCount),
                    missionRewardOutboxProperties.getMaxAttempts()
            );
        });
    }

    private record CharacterExpDispatchCommand(
            Long outboxId,
            Long missionId,
            Long userId,
            Long characterId,
            String difficulty,
            int expAmount,
            String idempotencyKey
    ) {
    }
}
