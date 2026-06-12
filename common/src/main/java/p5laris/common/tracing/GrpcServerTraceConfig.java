package p5laris.common.tracing;

import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor")
public class GrpcServerTraceConfig {

    @GrpcGlobalServerInterceptor
    public ServerInterceptor traceServerInterceptor() {
        return new GrpcTraceServerInterceptor();
    }
}
