package p5laris.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "GRPC_SERVER_PORT=0",
        "DB_URL=jdbc:postgresql://localhost:5432/po_notification",
        "DB_USERNAME=root",
        "DB_PASSWORD=12345678",
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9097",
        "EVENT_LOG_GRPC_ADDRESS=static://localhost:9099",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token",
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class NotificationApplicationTests {

    @Test
    void contextLoads() {
    }

}
