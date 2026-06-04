package p5laris.mission.domain.application.time;

import java.time.LocalDateTime;

/**
 * 미션 생성 시점의 하루 시간대를 표현한다.
 *
 * 현재는 한국 시간 기준 Clock을 mission 모듈에서 주입받으므로,
 * 이 enum은 전달받은 LocalDateTime의 hour만 보고 정책 시간대를 계산한다.
 */
public enum MissionTimeSlot {
    LATE_NIGHT,
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    public static MissionTimeSlot from(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("시간대 계산 기준 시각은 비어 있을 수 없습니다.");
        }
        return fromHour(dateTime.getHour());
    }

    public static MissionTimeSlot fromHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("시간 값은 0부터 23 사이여야 합니다.");
        }
        if (hour < 6) {
            return LATE_NIGHT;
        }
        if (hour < 11) {
            return MORNING;
        }
        if (hour < 17) {
            return AFTERNOON;
        }
        if (hour < 21) {
            return EVENING;
        }
        return NIGHT;
    }
}
