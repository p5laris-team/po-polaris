package p5laris.common.tracing;

import io.grpc.Context;
import io.grpc.Metadata;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HTTP, gRPC, 로그 MDC에서 공통으로 사용할 traceId 규칙을 관리한다.
 * requestId는 AI 멱등성 키이고, traceId는 여러 모듈 로그를 묶는 요청 추적 번호다.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_METADATA_KEY_NAME = "x-trace-id";
    public static final String MDC_KEY = "traceId";
    public static final Metadata.Key<String> TRACE_ID_METADATA_KEY = Metadata.Key.of(
            TRACE_ID_METADATA_KEY_NAME,
            Metadata.ASCII_STRING_MARSHALLER
    );
    public static final Context.Key<String> GRPC_CONTEXT_KEY = Context.key("traceId");

    private static final int MAX_TRACE_ID_LENGTH = 80;
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,79}");

    private TraceContext() {
    }

    public static String normalizeOrNew(String candidate) {
        String normalized = normalize(candidate);
        return normalized != null ? normalized : newTraceId();
    }

    public static String currentOrNew() {
        String currentTraceId = currentTraceId();
        return currentTraceId != null ? currentTraceId : newTraceId();
    }

    public static String currentTraceId() {
        String mdcTraceId = normalize(MDC.get(MDC_KEY));
        if (mdcTraceId != null) {
            return mdcTraceId;
        }

        return normalize(GRPC_CONTEXT_KEY.get());
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public static MdcScope openMdcScope(String traceId) {
        return new MdcScope(normalizeOrNew(traceId));
    }

    private static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }

        String trimmed = candidate.trim();
        if (trimmed.length() > MAX_TRACE_ID_LENGTH || !SAFE_TRACE_ID.matcher(trimmed).matches()) {
            return null;
        }

        return trimmed;
    }

    public static final class MdcScope implements AutoCloseable {

        private final String previousTraceId;
        private final boolean hadPreviousTraceId;

        private MdcScope(String traceId) {
            this.previousTraceId = MDC.get(MDC_KEY);
            this.hadPreviousTraceId = previousTraceId != null;
            MDC.put(MDC_KEY, traceId);
        }

        @Override
        public void close() {
            if (hadPreviousTraceId) {
                MDC.put(MDC_KEY, previousTraceId);
                return;
            }

            MDC.remove(MDC_KEY);
        }
    }
}
