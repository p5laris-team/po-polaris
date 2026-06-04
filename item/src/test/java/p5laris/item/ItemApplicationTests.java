package p5laris.item;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class ItemApplicationTests {

    @Test
    void contextLoads() {
    }

}
