package p5laris.user.domain.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.user.domain.domain.dto.AnswerOption;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum OnboardingQuestion {
    ROUTINE_GOAL("지금 만들고 싶은 작은 루틴은 무엇인가요?", true, 3, List.of(
            new AnswerOption("HYDRATION_MEAL", "물이나 식사를 챙기고 싶어요"),
            new AnswerOption("SPACE_RESET", "주변을 조금 정리하고 싶어요"),
            new AnswerOption("LIGHT_MOVEMENT", "가볍게 몸을 풀고 싶어요"),
            new AnswerOption("EXERCISE_HABIT", "운동 습관을 만들고 싶어요"),
            new AnswerOption("REST_RECOVERY", "쉬는 시간을 잘 만들고 싶어요"),
            new AnswerOption("MOOD_RECORD", "마음을 짧게 기록하고 싶어요"),
            new AnswerOption("FOCUS_START", "공부나 작업을 시작하고 싶어요"),
            new AnswerOption("SOCIAL_LIGHT", "사람들과 가볍게 연결되고 싶어요"),
            new AnswerOption("OUTDOOR_SUNLIGHT", "바깥 공기나 햇빛을 자주 보고 싶어요")
    )),
    PREFERRED_MISSION_TIME("미션은 언제 받는 게 편해요?", true, 5, List.of(
            new AnswerOption("MORNING", "오전"),
            new AnswerOption("AFTERNOON", "오후"),
            new AnswerOption("EVENING", "저녁"),
            new AnswerOption("NIGHT", "밤"),
            new AnswerOption("ANYTIME", "아무 때나")
    )),
    MISSION_PLACE_CONTEXT("주로 어디서 할 수 있는 미션이 좋아요?", true, 3, List.of(
            new AnswerOption("HOME", "집에서"),
            new AnswerOption("WORK_SCHOOL", "회사나 학교에서"),
            new AnswerOption("COMMUTE", "이동 중에"),
            new AnswerOption("OUTSIDE", "밖에서"),
            new AnswerOption("BED_REST", "침대나 쉬는 중에")
    )),
    MISSION_INTENSITY("미션 강도는 어느 정도가 좋아요?", false, 1, List.of(
            new AnswerOption("VERY_LIGHT", "정말 가벼운 것"),
            new AnswerOption("LIGHT", "3분 안에 할 수 있는 것"),
            new AnswerOption("NORMAL", "조금 움직여도 괜찮아")
    )),
    AVOIDED_MISSION_TAGS("피하고 싶은 미션이 있나요?", true, 5, List.of(
            new AnswerOption("OUTDOOR", "밖에 나가기"),
            new AnswerOption("SOCIAL_CONTACT", "사람에게 연락하기"),
            new AnswerOption("HEAVY_MOVEMENT", "몸을 많이 움직이기"),
            new AnswerOption("LONG_WRITING", "글로 길게 적기"),
            new AnswerOption("NOISY_ACTION", "소리 나는 행동"),
            new AnswerOption("NONE", "딱히 없어요")
    ));

    private final String content;
    private final boolean multipleSelection;
    private final int maxSelectionCount;
    private final List<AnswerOption> options;
}
