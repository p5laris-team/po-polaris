package p5laris.character.domain.api.advice;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import p5laris.character.domain.exception.CharacterException;

@GrpcAdvice
@Slf4j
public class CharacterGrpcExceptionHandler {

    private static final String INVALID_ARGUMENT_DESCRIPTION = "캐릭터 요청 값이 올바르지 않습니다.";

    @GrpcExceptionHandler(CharacterException.class)
    public StatusRuntimeException handleCharacterException(CharacterException e) {
        return Status.INVALID_ARGUMENT
                .withDescription(e.getErrorCode().getCode())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("캐릭터 gRPC 요청 값 검증 실패", e);
        return Status.INVALID_ARGUMENT
                .withDescription(INVALID_ARGUMENT_DESCRIPTION)
                .asRuntimeException();
    }
}
