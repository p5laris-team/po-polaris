package p5laris.character.domain.exception;

import lombok.Getter;

@Getter
public class CharacterException extends RuntimeException {
    private final CharacterErrorCode errorCode;

    public CharacterException(CharacterErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
