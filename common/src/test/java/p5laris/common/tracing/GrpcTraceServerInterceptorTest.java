package p5laris.common.tracing;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GrpcTraceServerInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void exposesIncomingTraceIdThroughContextAndMdc() {
        Metadata headers = new Metadata();
        headers.put(TraceContext.TRACE_ID_METADATA_KEY, "trace-server-1234");
        CapturingHandler handler = new CapturingHandler();

        MDC.put(TraceContext.MDC_KEY, "trace-parent-1234");
        ServerCall.Listener<String> listener = new GrpcTraceServerInterceptor().interceptCall(
                new NoOpServerCall<>(),
                headers,
                handler
        );

        assertEquals("trace-server-1234", handler.contextTraceId);
        assertEquals("trace-server-1234", handler.startMdcTraceId);
        assertEquals("trace-parent-1234", MDC.get(TraceContext.MDC_KEY));

        listener.onMessage("request");
        listener.onHalfClose();
        listener.onReady();
        listener.onComplete();
        listener.onCancel();

        assertEquals(
                List.of(
                        "trace-server-1234",
                        "trace-server-1234",
                        "trace-server-1234",
                        "trace-server-1234",
                        "trace-server-1234"
                ),
                handler.callbackTraceIds
        );
        assertEquals("trace-parent-1234", MDC.get(TraceContext.MDC_KEY));
    }

    @Test
    void replacesUnsafeTraceId() {
        Metadata headers = new Metadata();
        headers.put(TraceContext.TRACE_ID_METADATA_KEY, "../../unsafe");
        CapturingHandler handler = new CapturingHandler();

        new GrpcTraceServerInterceptor().interceptCall(
                new NoOpServerCall<>(),
                headers,
                handler
        );

        assertNotEquals("../../unsafe", handler.contextTraceId);
        assertEquals(36, handler.contextTraceId.length());
    }

    private static final class CapturingHandler
            implements ServerCallHandler<String, String> {

        private String contextTraceId;
        private String startMdcTraceId;
        private final List<String> callbackTraceIds = new ArrayList<>();

        @Override
        public ServerCall.Listener<String> startCall(
                ServerCall<String, String> call,
                Metadata headers
        ) {
            contextTraceId = TraceContext.GRPC_CONTEXT_KEY.get();
            startMdcTraceId = MDC.get(TraceContext.MDC_KEY);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    captureTraceId();
                }

                @Override
                public void onHalfClose() {
                    captureTraceId();
                }

                @Override
                public void onCancel() {
                    captureTraceId();
                }

                @Override
                public void onComplete() {
                    captureTraceId();
                }

                @Override
                public void onReady() {
                    captureTraceId();
                }

                private void captureTraceId() {
                    callbackTraceIds.add(MDC.get(TraceContext.MDC_KEY));
                }
            };
        }
    }

    private static final class NoOpServerCall<ReqT, RespT>
            extends ServerCall<ReqT, RespT> {

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(RespT message) {
        }

        @Override
        public void close(io.grpc.Status status, Metadata trailers) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
            return null;
        }
    }
}
