package p5laris.mission.domain.application.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;
import p5laris.mission.domain.infrastructure.grpc.UserWeatherRegionClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultWeatherLocationResolverTest {

    private final MissionWeatherProperties properties = properties();
    private final UserWeatherRegionClient userWeatherRegionClient = mock(UserWeatherRegionClient.class);
    private final DefaultWeatherLocationResolver resolver = new DefaultWeatherLocationResolver(
            properties,
            userWeatherRegionClient
    );

    @Test
    @DisplayName("사용자 선택 날씨 권역이 있으면 USER_SELECTED 좌표를 우선 사용한다")
    void resolve_userSelectedRegion() {
        when(userWeatherRegionClient.findWeatherRegion(1L))
                .thenReturn(Optional.of(new UserWeatherRegionClient.UserWeatherRegionSnapshot(
                        "GYEONGGI_SOUTH",
                        "경기 남부",
                        60,
                        121
                )));

        Optional<WeatherLocation> result = resolver.resolve(1L);

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo(WeatherLocationSource.USER_SELECTED);
        assertThat(result.get().locationLabel()).isEqualTo("경기 남부");
        assertThat(result.get().nx()).isEqualTo(60);
        assertThat(result.get().ny()).isEqualTo(121);
    }

    @Test
    @DisplayName("사용자 선택 날씨 권역이 없으면 운영 기본 좌표를 사용한다")
    void resolve_defaultRegion() {
        when(userWeatherRegionClient.findWeatherRegion(1L)).thenReturn(Optional.empty());

        Optional<WeatherLocation> result = resolver.resolve(1L);

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo(WeatherLocationSource.SERVICE_DEFAULT);
        assertThat(result.get().locationLabel()).isEqualTo("SEOUL");
        assertThat(result.get().nx()).isEqualTo(60);
        assertThat(result.get().ny()).isEqualTo(127);
    }

    private MissionWeatherProperties properties() {
        MissionWeatherProperties properties = new MissionWeatherProperties();
        properties.setDefaultNx(60);
        properties.setDefaultNy(127);
        properties.setDefaultLocationLabel("SEOUL");
        return properties;
    }
}
