package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.application.dto.CharacterTalkStreamMetadata;

/**
 * 별친구 대화 chunk를 gRPC/SSE 같은 전송 계층으로 내보내기 위한 application port다.
 *
 * AI application service가 gRPC StreamObserver에 직접 의존하지 않도록 분리한다.
 */
public interface CharacterTalkStreamEmitter {

    void emitMeta(CharacterTalkStreamMetadata metadata);

    void emitDelta(String text);

    void complete(boolean fallbackUsed, AiErrorType errorType, AiTokenUsage tokenUsage);

    default void complete(boolean fallbackUsed, AiErrorType errorType) {
        complete(fallbackUsed, errorType, AiTokenUsage.empty());
    }

    boolean hasEmittedDelta();
}
