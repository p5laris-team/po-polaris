package p5laris.gateway.global.tracing;

import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import p5laris.common.deadline.GrpcDeadlineClientInterceptor;
import p5laris.common.tracing.GrpcTraceClientInterceptor;

/**
 * gateway에서 각 도메인 모듈로 나가는 gRPC 호출에 현재 traceId를 전파한다.
 */
@Configuration
public class GrpcTraceClientConfig {

    @Value("${grpc.default-deadline-ms:5000}")
    private long defaultDeadlineMs;

    @GrpcGlobalClientInterceptor
    public ClientInterceptor traceClientInterceptor() {
        return new GrpcTraceClientInterceptor();
    }

    @GrpcGlobalClientInterceptor
    public ClientInterceptor deadlineClientInterceptor() {
        return new GrpcDeadlineClientInterceptor(defaultDeadlineMs);
    }
}
