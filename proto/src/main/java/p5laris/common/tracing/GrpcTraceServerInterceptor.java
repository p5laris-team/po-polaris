package p5laris.common.tracing;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC metadata의 traceId를 현재 gRPC Context와 로그 MDC에 연결한다.
 */
public class GrpcTraceServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String traceId = TraceContext.normalizeOrNew(headers.get(TraceContext.TRACE_ID_METADATA_KEY));
        Context context = Context.current().withValue(TraceContext.GRPC_CONTEXT_KEY, traceId);

        try (TraceContext.MdcScope ignored = TraceContext.openMdcScope(traceId)) {
            ServerCall.Listener<ReqT> listener = Contexts.interceptCall(context, call, headers, next);
            return new TraceServerCallListener<>(listener, traceId);
        }
    }

    private static final class TraceServerCallListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final String traceId;

        private TraceServerCallListener(ServerCall.Listener<ReqT> delegate, String traceId) {
            super(delegate);
            this.traceId = traceId;
        }

        @Override
        public void onMessage(ReqT message) {
            runWithTraceId(() -> super.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            runWithTraceId(super::onHalfClose);
        }

        @Override
        public void onCancel() {
            runWithTraceId(super::onCancel);
        }

        @Override
        public void onComplete() {
            runWithTraceId(super::onComplete);
        }

        @Override
        public void onReady() {
            runWithTraceId(super::onReady);
        }

        private void runWithTraceId(Runnable action) {
            try (TraceContext.MdcScope ignored = TraceContext.openMdcScope(traceId)) {
                action.run();
            }
        }
    }
}
