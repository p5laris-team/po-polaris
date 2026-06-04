package p5laris.common.deadline;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 클라이언트 호출 시 deadline(제한시간)이 설정되어 있지 않은 경우,
 * 설정된 기본 제한시간을 적용하여 스레드 무한 대기 현상을 방지합니다.
 */
public class GrpcDeadlineClientInterceptor implements ClientInterceptor {

    private final long defaultDeadlineMs;

    private static final long EVENT_LOG_DEADLINE_MS = 1000L;

    public GrpcDeadlineClientInterceptor(long defaultDeadlineMs) {
        this.defaultDeadlineMs = defaultDeadlineMs;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        if (callOptions.getDeadline() == null) {
            long deadlineMs = defaultDeadlineMs;
            if (method.getFullMethodName() != null) {
                if (method.getFullMethodName().contains("EventLogService") ||
                    method.getFullMethodName().contains("AiService")) {
                    deadlineMs = EVENT_LOG_DEADLINE_MS;
                }
            }
            if (deadlineMs > 0) {
                callOptions = callOptions.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
            }
        }
        return next.newCall(method, callOptions);
    }
}
