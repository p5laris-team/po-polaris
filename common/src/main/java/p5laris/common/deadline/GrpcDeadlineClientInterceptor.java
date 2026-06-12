package p5laris.common.deadline;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;

/**
 * gRPC 클라이언트 호출에 별도 deadline이 없으면 기본 제한 시간을 적용한다.
 */
public class GrpcDeadlineClientInterceptor implements ClientInterceptor {

    private static final long EVENT_LOG_DEADLINE_MS = 1000L;

    private final long defaultDeadlineMs;

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
                if (method.getFullMethodName().contains("EventLogService")
                        || method.getFullMethodName().contains("AiService")) {
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
