package p5laris.mission.domain.application.weather;

import java.util.Optional;

public interface WeatherLocationResolver {

    Optional<WeatherLocation> resolve(Long userId);
}
