package p5laris.character.domain.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Character Stat Types and their labels based on PRD 6.2
 */
@Getter
@RequiredArgsConstructor
public enum StatType {
    FULLNESS("든든함", "출출함", "배고픔"),
    ENERGY("말짱함", "졸림", "피곤함"),
    AFFECTION("가까움", "조용함", "쓸쓸함");

    private final String goodLabel;
    private final String normalLabel;
    private final String badLabel;

    public String getLabel(StatGrade grade) {
        return switch (grade) {
            case GOOD -> goodLabel;
            case NORMAL -> normalLabel;
            case BAD -> badLabel;
        };
    }
}
