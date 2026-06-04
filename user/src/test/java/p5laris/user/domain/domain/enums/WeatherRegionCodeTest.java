package p5laris.user.domain.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherRegionCodeTest {

    @Test
    @DisplayName("서울 대표 좌표를 기상청 격자 좌표로 변환한다")
    void seoulGrid() {
        WeatherRegionCode region = WeatherRegionCode.SEOUL;

        assertThat(region.nx()).isEqualTo(60);
        assertThat(region.ny()).isEqualTo(127);
    }

    @Test
    @DisplayName("사용자 입력 코드는 대소문자와 앞뒤 공백을 정규화한다")
    void fromCode_normalizesInput() {
        assertThat(WeatherRegionCode.fromCode(" gyeonggi_south "))
                .contains(WeatherRegionCode.GYEONGGI_SOUTH);
    }
}
