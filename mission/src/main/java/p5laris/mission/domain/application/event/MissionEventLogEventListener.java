package p5laris.mission.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * mission 비즈니스 이벤트를 트랜잭션 커밋 후 event-log 모듈로 전송한다.
 *
 * 로깅 실패가 미션 생성/완료 같은 핵심 흐름으로 전파되지 않도록 예외는 warn 로그로만 남긴다.
 */
@Slf4j
@Component
public class MissionEventLogEventListener {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.application.name:mission}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MissionEventLogEvent event) {
        try {
            eventLogStub.recordEventLog(toRequest(event));
        } catch (Exception e) {
            log.warn("미션 이벤트 로그 전송 실패. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }

    private RecordEventLogRequest toRequest(MissionEventLogEvent event) throws Exception {
        RecordEventLogRequest.Builder builder = RecordEventLogRequest.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(event.eventType())
                .setSourceService(sourceService)
                .setOccurredAt(toTimestamp(event.occurredAt().toInstant()));

        if (event.userId() != null) {
            builder.setUserId(event.userId());
        }
        if (event.refType() != null && !event.refType().isBlank()) {
            builder.setRefType(event.refType());
        }
        if (event.refId() != null) {
            builder.setRefId(event.refId());
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            builder.setPropertiesJson(objectMapper.writeValueAsString(event.metadata()));
        }

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
