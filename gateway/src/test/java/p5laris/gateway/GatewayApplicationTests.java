package p5laris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=0",
        "grpc.client.user.address=static://localhost:9091",
        "grpc.client.character.address=static://localhost:9092",
        "grpc.client.item.address=static://localhost:9093",
        "grpc.client.mission.address=static://localhost:9094",
        "grpc.client.ai.address=static://localhost:9095",
        "grpc.client.notification.address=static://localhost:9098",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "jwt.access-expiration-ms=3600000",
        "jwt.refresh-expiration-ms=1209600000",
        "app.public-base-url=https://p5laris.life",
        "app.default-share-image-url=https://p5laris.life/assets/share-placeholder.png",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class GatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
