package p5laris.common.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 내부 gRPC 호출의 공유 토큰을 검증하는 server interceptor다.
 */
public class GrpcInternalAuthServerInterceptor implements ServerInterceptor {

    private static final String AUTH_FAILED_DESCRIPTION = "내부 gRPC 인증에 실패했습니다.";

    private final boolean enabled;
    private final String expectedToken;

    public GrpcInternalAuthServerInterceptor(boolean enabled, String expectedToken) {
        this.enabled = enabled;
        this.expectedToken = expectedToken;
        validateToken(enabled, expectedToken);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        if (!enabled) {
            return next.startCall(call, headers);
        }

        String actualToken = headers.get(InternalGrpcAuth.TOKEN_METADATA_KEY);
        if (!tokenMatches(actualToken)) {
            call.close(Status.UNAUTHENTICATED.withDescription(AUTH_FAILED_DESCRIPTION), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        return next.startCall(call, headers);
    }

    private boolean tokenMatches(String actualToken) {
        if (expectedToken == null || expectedToken.isBlank() || actualToken == null || actualToken.isBlank()) {
            return false;
        }

        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private void validateToken(boolean enabled, String expectedToken) {
        if (enabled && (expectedToken == null || expectedToken.isBlank())) {
            throw new IllegalArgumentException("내부 gRPC 인증 토큰이 설정되지 않았습니다.");
        }
    }
}
