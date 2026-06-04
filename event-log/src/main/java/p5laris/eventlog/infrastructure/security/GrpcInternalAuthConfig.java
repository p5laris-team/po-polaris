package p5laris.eventlog.infrastructure.security;

import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import p5laris.common.security.GrpcInternalAuthServerInterceptor;

/**
 * event-log 모듈의 내부 gRPC 인증 토큰을 검증한다.
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
}
