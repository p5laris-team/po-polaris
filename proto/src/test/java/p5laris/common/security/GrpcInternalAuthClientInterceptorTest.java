package p5laris.common.security;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcInternalAuthClientInterceptorTest {

    @Test
    void enabled면_metadata에_내부_토큰을_싣는다() {
        CapturingClientCall<String, String> delegate = new CapturingClientCall<>();
        Channel channel = new CapturingChannel(delegate);
        GrpcInternalAuthClientInterceptor interceptor =
                new GrpcInternalAuthClientInterceptor(true, "local-token");

        ClientCall<String, String> call = interceptor.interceptCall(testMethod(), CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());

        assertEquals("local-token", delegate.headers.get(InternalGrpcAuth.TOKEN_METADATA_KEY));
    }

    @Test
    void disabled면_metadata에_내부_토큰을_싣지_않는다() {
        CapturingClientCall<String, String> delegate = new CapturingClientCall<>();
        Channel channel = new CapturingChannel(delegate);
        GrpcInternalAuthClientInterceptor interceptor =
                new GrpcInternalAuthClientInterceptor(false, "local-token");

        ClientCall<String, String> call = interceptor.interceptCall(testMethod(), CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());

        assertNull(delegate.headers.get(InternalGrpcAuth.TOKEN_METADATA_KEY));
    }

    @Test
    void enabled인데_토큰이_비어_있으면_생성에_실패한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GrpcInternalAuthClientInterceptor(true, "")
        );
    }

    private MethodDescriptor<String, String> testMethod() {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Service/Call")
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
    }

    private static class CapturingChannel extends Channel {
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

    private static class CapturingClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
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
}
