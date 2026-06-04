package p5laris.mission.domain.application.diversity;

/**
 * 같은 날 반복되면 지루하게 느껴지는 미션의 핵심 행동군이다.
 *
 * AI가 제목/설명을 살짝 바꿔도 결국 같은 행동이면 후검증에서 걸러내기 위해 사용한다.
 */
public enum MissionActionFamily {
    WATER_DRINK,
    BREATHING,
    DESK_RESET,
    ROOM_RESET,
    NECK_SHOULDER_STRETCH,
    BODY_STRETCH,
    SUNLIGHT,
    WALKING,
    JOURNALING,
    SLEEP_PREP,
    SOCIAL_CONTACT,
    FOCUS_RESET,
    MOOD_CHECK,
    UNKNOWN
}
