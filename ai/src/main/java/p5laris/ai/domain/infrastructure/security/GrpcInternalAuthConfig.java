package p5laris.ai.domain.infrastructure.security;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import p5laris.common.security.GrpcInternalAuthClientInterceptor;
import p5laris.common.security.GrpcInternalAuthServerInterceptor;

/**
 * AI 모듈의 내부 gRPC 인증 토큰을 검증하고 event-log 같은 하위 gRPC 호출에도 전달한다.
 */
@Configuration
public class GrpcInternalAuthConfig {

    @GrpcGlobalServerInterceptor
    public ServerInterceptor internalAuthServerInterceptor(
            @Value("${internal.grpc-auth.enabled}") boolean enabled,
            @Value("${internal.grpc-auth.token}") String token
    ) {
        return new GrpcInternalAuthServerInterceptor(enabled, token);
    }

    @GrpcGlobalClientInterceptor
    public ClientInterceptor internalAuthClientInterceptor(
            @Value("${internal.grpc-auth.enabled}") boolean enabled,
            @Value("${internal.grpc-auth.token}") String token
    ) {
        return new GrpcInternalAuthClientInterceptor(enabled, token);
    }
}
