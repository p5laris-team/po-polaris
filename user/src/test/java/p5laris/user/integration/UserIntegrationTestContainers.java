package p5laris.user.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "server.port=0",
        "grpc.server.port=0",
        "grpc.client.character.address=static://localhost:19092",
        "grpc.client.item.address=static://localhost:19093",
        "grpc.client.mission.address=static://localhost:19094",
        "grpc.client.ai.address=static://localhost:19095",
        "grpc.client.event-log.address=static://localhost:19099",
        "grpc.client.notification.address=static://localhost:19098",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.task.scheduling.enabled=false",
        "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret",
        "jwt.access-expiration-ms=3600000",
        "jwt.refresh-expiration-ms=1209600000",
        "oauth.google.client-id=test-client-id",
        "oauth.google.client-secret=test-client-secret",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token",
        "portone.store-id=test-store",
        "portone.api-secret=test-secret",
        "portone.mock-mode=true"
})
public abstract class UserIntegrationTestContainers {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_test")
                    .withUsername("test")
                    .withPassword("test");

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
