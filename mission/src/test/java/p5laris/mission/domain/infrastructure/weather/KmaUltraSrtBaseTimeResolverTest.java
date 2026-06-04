package p5laris.mission.domain.infrastructure.weather;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaUltraSrtBaseTimeResolverTest {

    private final KmaUltraSrtBaseTimeResolver resolver = new KmaUltraSrtBaseTimeResolver();

    @Test
    void 매시_45분_전에는_직전_시간_30분_발표분을_사용한다() {
        KmaBaseTime baseTime = resolver.resolve(LocalDateTime.of(2026, 6, 1, 12, 44));

        assertThat(baseTime.baseDate()).isEqualTo("20260601");
        assertThat(baseTime.baseTime()).isEqualTo("1130");
    }

    @Test
    void 매시_45분부터는_현재_시간_30분_발표분을_사용한다() {
        KmaBaseTime baseTime = resolver.resolve(LocalDateTime.of(2026, 6, 1, 12, 45));

        assertThat(baseTime.baseDate()).isEqualTo("20260601");
        assertThat(baseTime.baseTime()).isEqualTo("1230");
    }

    @Test
    void 자정_초반에는_전날_23시_30분_발표분을_사용한다() {
        KmaBaseTime baseTime = resolver.resolve(LocalDateTime.of(2026, 6, 1, 0, 20));

        assertThat(baseTime.baseDate()).isEqualTo("20260531");
        assertThat(baseTime.baseTime()).isEqualTo("2330");
    }
}
