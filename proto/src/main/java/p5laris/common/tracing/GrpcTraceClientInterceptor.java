package p5laris.common.tracing;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * 현재 MDC 또는 gRPC Context의 traceId를 다음 gRPC 호출 metadata에 싣는다.
 */
public class GrpcTraceClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)
        ) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(TraceContext.TRACE_ID_METADATA_KEY, TraceContext.currentOrNew());
                super.start(responseListener, headers);
            }
        };
    }
}
