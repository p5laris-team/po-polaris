package p5laris.mission.domain.infrastructure.weather;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 기상청 초단기예보 발표 시각을 계산한다.
 *
 * 초단기예보는 매시 30분 기준으로 생성되며, 너무 이른 시점에 현재 시각을 넣으면 데이터가 비어 있을 수 있다.
 * 그래서 45분 전에는 직전 시간의 30분 발표분을 사용한다.
 */
@Component
public class KmaUltraSrtBaseTimeResolver {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    public KmaBaseTime resolve(LocalDateTime now) {
        LocalDateTime baseDateTime = now.withMinute(30).withSecond(0).withNano(0);
        if (now.getMinute() < 45) {
            baseDateTime = baseDateTime.minusHours(1);
        }

        return new KmaBaseTime(
                DATE_FORMATTER.format(baseDateTime),
                TIME_FORMATTER.format(baseDateTime)
        );
    }
}
