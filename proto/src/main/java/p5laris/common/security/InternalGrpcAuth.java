package p5laris.common.security;

import io.grpc.Metadata;

/**
 * 내부 gRPC 호출 인증에 사용하는 공통 metadata 정의다.
 */
public final class InternalGrpcAuth {

    public static final String TOKEN_HEADER = "x-internal-grpc-token";
    public static final Metadata.Key<String> TOKEN_METADATA_KEY = Metadata.Key.of(
            TOKEN_HEADER,
            Metadata.ASCII_STRING_MARSHALLER
    );

    private InternalGrpcAuth() {
    }
}
