package p5laris.character.domain.exception;

public class KafkaConsumerProcessingException extends RuntimeException {

    public KafkaConsumerProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
