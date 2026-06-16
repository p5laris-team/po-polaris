package p5laris.common.tracing;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrpcTraceClientInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsCurrentMdcTraceIdToMetadata() {
        CapturingClientCall<String, String> delegate = new CapturingClientCall<>();
        GrpcTraceClientInterceptor interceptor = new GrpcTraceClientInterceptor();
        MDC.put(TraceContext.MDC_KEY, "trace-client-1234");

        ClientCall<String, String> call = interceptor.interceptCall(
                testMethod(),
                CallOptions.DEFAULT,
                new CapturingChannel(delegate)
        );
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());

        assertEquals(
                "trace-client-1234",
                delegate.headers.get(TraceContext.TRACE_ID_METADATA_KEY)
        );
    }

    @Test
    void usesGrpcContextAndGeneratesTraceIdWhenContextIsMissing() {
        CapturingClientCall<String, String> contextDelegate = new CapturingClientCall<>();
        Context context = Context.current()
                .withValue(TraceContext.GRPC_CONTEXT_KEY, "trace-context-1234");

        context.run(() -> startInterceptedCall(contextDelegate));

        assertEquals(
                "trace-context-1234",
                contextDelegate.headers.get(TraceContext.TRACE_ID_METADATA_KEY)
        );

        CapturingClientCall<String, String> generatedDelegate = new CapturingClientCall<>();
        startInterceptedCall(generatedDelegate);

        assertNotNull(generatedDelegate.headers.get(TraceContext.TRACE_ID_METADATA_KEY));
        assertEquals(
                36,
                generatedDelegate.headers.get(TraceContext.TRACE_ID_METADATA_KEY).length()
        );
    }

    private void startInterceptedCall(CapturingClientCall<String, String> delegate) {
        ClientCall<String, String> call = new GrpcTraceClientInterceptor().interceptCall(
                testMethod(),
                CallOptions.DEFAULT,
                new CapturingChannel(delegate)
        );
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
    }

    private MethodDescriptor<String, String> testMethod() {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.TraceService/Call")
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
    }

    private static final class CapturingChannel extends Channel {

        private final CapturingClientCall<String, String> call;

        private CapturingChannel(CapturingClientCall<String, String> call) {
            this.call = call;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor,
                CallOptions callOptions
        ) {
            @SuppressWarnings("unchecked")
            ClientCall<ReqT, RespT> typedCall = (ClientCall<ReqT, RespT>) call;
            return typedCall;
        }

        @Override
        public String authority() {
            return "test";
        }
    }

    private static final class CapturingClientCall<ReqT, RespT>
            extends ClientCall<ReqT, RespT> {

        private Metadata headers;

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            this.headers = headers;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(ReqT message) {
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {

        @Override
        public java.io.InputStream stream(String value) {
            return new java.io.ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String parse(java.io.InputStream stream) {
            try {
                return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
