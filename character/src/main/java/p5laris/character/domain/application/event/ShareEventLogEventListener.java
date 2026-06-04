package p5laris.character.domain.application.event;

import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class ShareEventLogEventListener {

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ShareEventLogEvent event) {
        try {
            eventLogStub.recordEventLog(toRequest(event));
        } catch (Exception e) {
            log.error("공유 이벤트 로그 gRPC 기록에 실패했습니다. eventType={}", event.eventType(), e);
        }
    }

    private RecordEventLogRequest toRequest(ShareEventLogEvent event) {
        RecordEventLogRequest.Builder builder = RecordEventLogRequest.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(event.eventType())
                .setSourceService("character")
                .setUserId(event.userId())
                .setRefType(event.refType())
                .setRefId(event.refId())
                .setPropertiesJson(event.getPropertiesJson())
                .setOccurredAt(toTimestamp(event.occurredAt().toInstant()));

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
