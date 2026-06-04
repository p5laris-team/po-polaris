package p5laris.common.tracing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TraceContextTest {

    @Test
    void 안전한_traceId는_그대로_사용한다() {
        String traceId = "trace-20260602-abc";

        assertEquals(traceId, TraceContext.normalizeOrNew(traceId));
    }

    @Test
    void 비정상_traceId는_새로_생성한다() {
        String traceId = TraceContext.normalizeOrNew("../../secret");

        assertNotEquals("../../secret", traceId);
        assertEquals(36, traceId.length());
    }
}
