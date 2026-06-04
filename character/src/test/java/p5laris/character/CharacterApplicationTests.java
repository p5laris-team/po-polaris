package p5laris.character;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/character",
        "spring.datasource.username=root",
        "spring.datasource.password=12345678",
        "grpc.server.port=0",
        "grpc.client.user.address=static://localhost:9091",
        "grpc.client.item.address=static://localhost:9093",
        "grpc.client.ai.address=static://localhost:9095",
        "grpc.client.notification.address=static://localhost:9098",
        "grpc.client.event-log.address=static://localhost:9099",
        "character.state-notification.enabled=false",
        "character.share-reward-wallet.deadline-ms=1000",
        "character.item-grpc.deadline-ms=1000",
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket-name=polaris-share-cards",
        "cloud.aws.s3.public-domain=https://cdn.p5laris.life",
        "cloud.aws.s3.cors.enabled=false",
        "cloud.aws.s3.cors.allowed-origins=http://127.0.0.1:5173,http://localhost:5173",
        "app.public-base-url=https://p5laris.life",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class CharacterApplicationTests {

    @Test
    void contextLoads() {
    }

}
