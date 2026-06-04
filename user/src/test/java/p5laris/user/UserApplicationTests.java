package p5laris.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=0",
        "grpc.server.port=0",
        "grpc.client.character.address=static://localhost:9092",
        "grpc.client.item.address=static://localhost:9093",
        "grpc.client.mission.address=static://localhost:9094",
        "grpc.client.ai.address=static://localhost:9095",
        "grpc.client.event-log.address=static://localhost:9099",
        "grpc.client.notification.address=static://localhost:9098",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "jwt.access-expiration-ms=3600000",
        "jwt.refresh-expiration-ms=1209600000",
        "oauth.google.client-id=test-client-id",
        "oauth.google.client-secret=test-client-secret",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class UserApplicationTests {

    @Test
    void contextLoads() {
    }

}
