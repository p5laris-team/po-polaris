package p5laris.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "spring.ai.model.embedding.text=none",
        "ai.embedding.enabled=false",
        "ai.embedding.model=gemini-embedding-001",
        "ai.embedding.dimension=768",
        "ai.circuit-breaker.enabled=false",
        "ai.circuit-breaker.sliding-window-size=10",
        "ai.circuit-breaker.minimum-number-of-calls=5",
        "ai.circuit-breaker.failure-rate-threshold=50",
        "ai.circuit-breaker.slow-call-duration-ms=3000",
        "ai.circuit-breaker.slow-call-rate-threshold=50",
        "ai.circuit-breaker.wait-duration-open-ms=30000",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class AiApplicationTests {

    @Test
    void contextLoads() {
    }

}
