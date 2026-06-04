package p5laris.mission.domain.application;

import org.springframework.stereotype.Component;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;

@Component
public class MissionCharacterExpPolicy {

    private static final int EASY_EXP = 10;
    private static final int NORMAL_EXP = 15;
    private static final int CHALLENGE_EXP = 30;

    public int calculateExp(MissionDifficultyType difficulty) {
        if (difficulty == null) {
            return EASY_EXP;
        }
        return switch (difficulty) {
            case EASY -> EASY_EXP;
            case NORMAL -> NORMAL_EXP;
            case CHALLENGE -> CHALLENGE_EXP;
        };
    }
}
