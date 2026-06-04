package p5laris.character.domain.domain.enums;

import lombok.Getter;

/**
 * Character Status Grades (PRD 6.3)
 */
@Getter
public enum StatGrade {
    GOOD(70, 100),
    NORMAL(40, 69),
    BAD(0, 39);

    private final int min;
    private final int max;

    StatGrade(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static StatGrade fromValue(int value) {
        if (value >= GOOD.min) return GOOD;
        if (value >= NORMAL.min) return NORMAL;
        return BAD;
    }
}
