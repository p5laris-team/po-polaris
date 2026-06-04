package p5laris.mission.domain.application.weather;

import java.util.Optional;

public record WeatherCacheLookup(
        boolean available,
        Optional<WeatherSnapshot> snapshot
) {

    public static WeatherCacheLookup hit(WeatherSnapshot snapshot) {
        return new WeatherCacheLookup(true, Optional.of(snapshot));
    }

    public static WeatherCacheLookup miss() {
        return new WeatherCacheLookup(true, Optional.empty());
    }

    public static WeatherCacheLookup unavailable() {
        return new WeatherCacheLookup(false, Optional.empty());
    }
}
