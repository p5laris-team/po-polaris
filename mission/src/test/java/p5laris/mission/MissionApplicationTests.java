package p5laris.mission;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "grpc.client.notification.address=static://localhost:9098",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.data.redis.timeout=200ms",
        "mission.weather.enabled=false",
        "mission.weather.provider=kma",
        "mission.weather.default-nx=60",
        "mission.weather.default-ny=127",
        "mission.weather.default-location-label=SEOUL",
        "mission.weather.timeout-ms=1500",
        "mission.weather.cache-ttl-seconds=1800",
        "mission.weather.redis-cache-enabled=false",
        "mission.weather.rate-limit-enabled=false",
        "mission.weather.rate-limit-requests-per-minute=60",
        "mission.weather.rate-limit-key-ttl-seconds=70",
        "mission.weather.rate-limit-fail-closed=true",
        "mission.weather.kma.base-url=http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0",
        "mission.weather.kma.service-key=test-key",
        "mission.rag.enabled=false",
        "mission.rag.embedding-model=gemini-embedding-001",
        "mission.rag.embedding-dimension=768",
        "mission.rag.top-k=5",
        "mission.rag.similarity-threshold=0.72",
        "mission.rag.fallback-to-recent-memory=true",
        "mission.memory-embedding.enabled=false",
        "mission.memory-embedding.fixed-delay-ms=60000",
        "mission.memory-embedding.initial-delay-ms=60000",
        "mission.memory-embedding.batch-size=20",
        "mission.memory-embedding.max-attempts=3",
        "mission.memory-embedding.processing-timeout-seconds=60",
        "mission.memory-embedding.retry-initial-delay-seconds=60",
        "mission.memory-embedding.retry-max-delay-seconds=1800",
        "mission.reward-outbox.enabled=false",
        "mission.reward-outbox.fixed-delay-ms=60000",
        "mission.reward-outbox.initial-delay-ms=60000",
        "mission.reward-outbox.batch-size=10",
        "mission.reward-outbox.max-attempts=5",
        "mission.reward-outbox.processing-timeout-seconds=60",
        "mission.reward-outbox.retry-initial-delay-seconds=60",
        "mission.reward-outbox.retry-max-delay-seconds=3600",
        "mission.reward-wallet.deadline-ms=1000",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class MissionApplicationTests {

    @Test
    void contextLoads() {
    }

}
