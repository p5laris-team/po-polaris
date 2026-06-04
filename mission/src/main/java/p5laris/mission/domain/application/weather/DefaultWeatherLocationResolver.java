package p5laris.mission.domain.application.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;
import p5laris.mission.domain.infrastructure.grpc.UserWeatherRegionClient;

import java.util.Optional;

/**
 * 사용자가 선택한 날씨 권역을 우선 사용하고, 없거나 조회에 실패하면 운영 기본 격자 좌표로 내려간다.
 */
@Component
@RequiredArgsConstructor
public class DefaultWeatherLocationResolver implements WeatherLocationResolver {

    private final MissionWeatherProperties properties;
    private final UserWeatherRegionClient userWeatherRegionClient;

    @Override
    public Optional<WeatherLocation> resolve(Long userId) {
        Optional<WeatherLocation> userSelectedLocation = userWeatherRegionClient.findWeatherRegion(userId)
                .map(region -> new WeatherLocation(
                        region.nx(),
                        region.ny(),
                        region.displayName(),
                        WeatherLocationSource.USER_SELECTED
                ));
        if (userSelectedLocation.isPresent()) {
            return userSelectedLocation;
        }

        return defaultLocation();
    }

    private Optional<WeatherLocation> defaultLocation() {
        if (!properties.hasDefaultLocation()) {
            return Optional.empty();
        }

        return Optional.of(new WeatherLocation(
                properties.getDefaultNx(),
                properties.getDefaultNy(),
                properties.getDefaultLocationLabel().trim(),
                WeatherLocationSource.SERVICE_DEFAULT
        ));
    }
}
