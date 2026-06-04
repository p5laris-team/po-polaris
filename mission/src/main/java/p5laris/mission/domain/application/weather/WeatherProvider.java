package p5laris.mission.domain.application.weather;

import java.time.LocalDateTime;

public interface WeatherProvider {

    boolean supports(String provider);

    String sourceName();

    String cacheKey(WeatherLocation location, LocalDateTime now);

    WeatherSnapshot fetch(WeatherLocation location, LocalDateTime now);
}
