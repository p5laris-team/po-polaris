package p5laris.common.outbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxBackoffPolicyTest {

    @Test
    void retryDelayDoublesAndStopsAtMaximum() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);

        assertEquals(now.plusSeconds(60), OutboxBackoffPolicy.nextAttemptAt(now, -1, 60, 600));
        assertEquals(now.plusSeconds(120), OutboxBackoffPolicy.nextAttemptAt(now, 2, 60, 600));
        assertEquals(now.plusSeconds(600), OutboxBackoffPolicy.nextAttemptAt(now, 5, 60, 600));
        assertEquals(now.plusSeconds(600), OutboxBackoffPolicy.nextAttemptAt(now, 20, 60, 600));
    }

    @Test
    void defaultDelayUsesSixtySecondsAndCapsAtThirtyMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);

        assertEquals(now.plusSeconds(60), OutboxBackoffPolicy.nextAttemptAt(now, 1));
        assertEquals(now.plusSeconds(1800), OutboxBackoffPolicy.nextAttemptAt(now, 20));
    }

    @Test
    void customDelaysPreserveModuleSpecificRetrySettings() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);

        assertEquals(now.plusSeconds(30), OutboxBackoffPolicy.nextAttemptAt(now, 0, 30, 300));
        assertEquals(now.plusSeconds(240), OutboxBackoffPolicy.nextAttemptAt(now, 4, 30, 300));
        assertEquals(now.plusSeconds(300), OutboxBackoffPolicy.nextAttemptAt(now, 20, 30, 300));
        assertEquals(now.plusSeconds(120), OutboxBackoffPolicy.nextAttemptAt(now, 4, 15, 120));
    }
}
