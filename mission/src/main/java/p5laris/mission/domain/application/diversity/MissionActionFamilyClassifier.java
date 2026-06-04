package p5laris.mission.domain.application.diversity;

import java.util.List;
import java.util.Locale;

/**
 * 미션 제목/설명에서 핵심 행동군을 가볍게 추출한다.
 *
 * embedding 유사도보다 빠르고 예측 가능한 후검증용 분류기라,
 * 확실히 같은 행동이라고 볼 수 있는 키워드만 보수적으로 매핑한다.
 */
public final class MissionActionFamilyClassifier {

    private MissionActionFamilyClassifier() {
    }

    public static MissionActionFamily classify(String title, String description) {
        String text = normalize(title) + " " + normalize(description);
        if (text.isBlank()) {
            return MissionActionFamily.UNKNOWN;
        }

        if (containsAny(text, "물", "수분", "한모금", "마시")) {
            return MissionActionFamily.WATER_DRINK;
        }
        if (containsAny(text, "책상", "모니터", "키보드", "마우스", "작업공간", "업무공간")) {
            return MissionActionFamily.DESK_RESET;
        }
        if (containsAny(text, "방청소", "방정리", "침대정리", "바닥", "쓰레기통", "휴지통", "수납")) {
            return MissionActionFamily.ROOM_RESET;
        }
        if (containsAny(text, "목", "어깨", "승모근", "날개뼈")) {
            return MissionActionFamily.NECK_SHOULDER_STRETCH;
        }
        if (containsAny(text, "스트레칭", "기지개", "손목", "허리", "종아리", "발목", "다리", "몸풀")) {
            return MissionActionFamily.BODY_STRETCH;
        }
        if (containsAny(text, "숨", "호흡", "들이쉬", "내쉬", "숨고르")) {
            return MissionActionFamily.BREATHING;
        }
        if (containsAny(text, "햇빛", "햇살", "햇볕", "창가", "빛충전")) {
            return MissionActionFamily.SUNLIGHT;
        }
        if (containsAny(text, "산책", "걷기", "걸어", "걸음", "발걸음")) {
            return MissionActionFamily.WALKING;
        }
        if (containsAny(text, "기록", "일기", "메모", "적어", "써보", "문장")) {
            return MissionActionFamily.JOURNALING;
        }
        if (containsAny(text, "잠", "수면", "침대", "불끄", "자기전", "잠들")) {
            return MissionActionFamily.SLEEP_PREP;
        }
        if (containsAny(text, "연락", "메시지", "문자", "전화", "카톡", "안부", "dm")) {
            return MissionActionFamily.SOCIAL_CONTACT;
        }
        if (containsAny(text, "집중", "타이머", "알림끄", "방해금지", "할일", "우선순위")) {
            return MissionActionFamily.FOCUS_RESET;
        }
        if (containsAny(text, "기분", "감정", "마음", "컨디션", "상태체크")) {
            return MissionActionFamily.MOOD_CHECK;
        }
        return MissionActionFamily.UNKNOWN;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private static boolean containsAny(String text, String... keywords) {
        return List.of(keywords).stream().anyMatch(text::contains);
    }
}
