package p5laris.common.security;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcInternalAuthServerInterceptorTest {

    @Test
    void 토큰이_없으면_unauthenticated로_차단한다() {
        GrpcInternalAuthServerInterceptor interceptor =
                new GrpcInternalAuthServerInterceptor(true, "local-token");
        CapturingServerCall<String, String> call = new CapturingServerCall<>();
        CapturingServerCallHandler<String, String> handler = new CapturingServerCallHandler<>();

        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handler.called);
        assertEquals(Status.Code.UNAUTHENTICATED, call.status.getCode());
    }

    @Test
    void 잘못된_토큰이면_unauthenticated로_차단한다() {
        GrpcInternalAuthServerInterceptor interceptor =
                new GrpcInternalAuthServerInterceptor(true, "local-token");
        CapturingServerCall<String, String> call = new CapturingServerCall<>();
        CapturingServerCallHandler<String, String> handler = new CapturingServerCallHandler<>();
        Metadata headers = new Metadata();
        headers.put(InternalGrpcAuth.TOKEN_METADATA_KEY, "wrong-token");

        interceptor.interceptCall(call, headers, handler);

        assertFalse(handler.called);
        assertEquals(Status.Code.UNAUTHENTICATED, call.status.getCode());
    }

    @Test
    void 올바른_토큰이면_handler로_넘긴다() {
        GrpcInternalAuthServerInterceptor interceptor =
                new GrpcInternalAuthServerInterceptor(true, "local-token");
        CapturingServerCall<String, String> call = new CapturingServerCall<>();
        CapturingServerCallHandler<String, String> handler = new CapturingServerCallHandler<>();
        Metadata headers = new Metadata();
        headers.put(InternalGrpcAuth.TOKEN_METADATA_KEY, "local-token");

        interceptor.interceptCall(call, headers, handler);

        assertTrue(handler.called);
    }

    @Test
    void disabled면_토큰이_없어도_handler로_넘긴다() {
        GrpcInternalAuthServerInterceptor interceptor =
                new GrpcInternalAuthServerInterceptor(false, "local-token");
        CapturingServerCall<String, String> call = new CapturingServerCall<>();
        CapturingServerCallHandler<String, String> handler = new CapturingServerCallHandler<>();

        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handler.called);
    }

    @Test
    void enabled인데_토큰이_비어_있으면_생성에_실패한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GrpcInternalAuthServerInterceptor(true, "")
        );
    }

    private static class CapturingServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
        private Status status;

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
        public void close(Status status, Metadata trailers) {
            this.status = status;
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

    private static class CapturingServerCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
        private boolean called;

        @Override
        public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call, Metadata headers) {
            called = true;
            return new ServerCall.Listener<>() {
            };
        }
    }
}
