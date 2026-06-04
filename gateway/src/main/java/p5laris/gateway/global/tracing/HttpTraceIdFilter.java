package p5laris.gateway.global.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import p5laris.common.tracing.TraceContext;

import java.io.IOException;

/**
 * gateway로 들어오는 HTTP 요청의 traceId를 발급하고 응답 헤더와 로그 MDC에 연결한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpTraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = TraceContext.normalizeOrNew(request.getHeader(TraceContext.TRACE_ID_HEADER));
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);

        try (TraceContext.MdcScope ignored = TraceContext.openMdcScope(traceId)) {
            filterChain.doFilter(request, response);
        }
    }
}
