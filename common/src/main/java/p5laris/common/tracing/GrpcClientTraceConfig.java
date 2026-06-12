package p5laris.common.tracing;

import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import p5laris.common.deadline.GrpcDeadlineClientInterceptor;

@Configuration
@ConditionalOnClass(name = "net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor")
public class GrpcClientTraceConfig {

    @Value("${grpc.default-deadline-ms:3000}")
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
