package p5laris.common.security;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * 내부 gRPC 호출 metadata에 공유 토큰을 싣는 client interceptor다.
 */
public class GrpcInternalAuthClientInterceptor implements ClientInterceptor {

    private final boolean enabled;
    private final String token;

    public GrpcInternalAuthClientInterceptor(boolean enabled, String token) {
        this.enabled = enabled;
        this.token = token;
        validateToken(enabled, token);
    }

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
                if (enabled && token != null && !token.isBlank()) {
                    headers.put(InternalGrpcAuth.TOKEN_METADATA_KEY, token);
                }
                super.start(responseListener, headers);
            }
        };
    }

    private void validateToken(boolean enabled, String token) {
        if (enabled && (token == null || token.isBlank())) {
            throw new IllegalArgumentException("내부 gRPC 인증 토큰이 설정되지 않았습니다.");
        }
    }
}
