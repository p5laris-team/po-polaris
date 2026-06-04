package p5laris.user.infrastructure.config;

import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import p5laris.common.deadline.GrpcDeadlineClientInterceptor;

/**
 * user 모듈에서 나가는 모든 gRPC 호출에 대해 기본 deadline(제한시간)을 설정하는 인터셉터를 등록합니다.
 */
@Configuration
public class GrpcClientConfig {

    @Value("${grpc.default-deadline-ms:3000}")
    private long defaultDeadlineMs;

    @GrpcGlobalClientInterceptor
    public ClientInterceptor deadlineClientInterceptor() {
        return new GrpcDeadlineClientInterceptor(defaultDeadlineMs);
    }
}
