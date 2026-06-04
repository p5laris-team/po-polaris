package p5laris.gateway.global.security;

import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import p5laris.common.security.GrpcInternalAuthClientInterceptor;

/**
 * gateway에서 내부 도메인 모듈로 나가는 gRPC 호출에 내부 인증 토큰을 붙인다.
 */
@Configuration
public class GrpcInternalAuthClientConfig {

    @GrpcGlobalClientInterceptor
    public ClientInterceptor internalAuthClientInterceptor(
            @Value("${internal.grpc-auth.enabled}") boolean enabled,
            @Value("${internal.grpc-auth.token}") String token
    ) {
        return new GrpcInternalAuthClientInterceptor(enabled, token);
    }
}
