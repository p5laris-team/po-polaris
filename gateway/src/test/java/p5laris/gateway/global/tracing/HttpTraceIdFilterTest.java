package p5laris.gateway.global.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import p5laris.common.tracing.TraceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HttpTraceIdFilterTest {

    private final HttpTraceIdFilter filter = new HttpTraceIdFilter();

    @Test
    void 요청_traceId가_안전하면_응답_헤더에_그대로_내려준다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "trace-20260602-001");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("trace-20260602-001", response.getHeader(TraceContext.TRACE_ID_HEADER));
    }

    @Test
    void 요청_traceId가_비정상이면_새_traceId를_내려준다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "../../secret");

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader(TraceContext.TRACE_ID_HEADER);
        assertNotEquals("../../secret", traceId);
        assertEquals(36, traceId.length());
    }
}
