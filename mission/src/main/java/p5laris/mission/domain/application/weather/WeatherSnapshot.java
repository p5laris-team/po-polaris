package p5laris.mission.domain.application.weather;

import java.util.List;

public record WeatherSnapshot(
        String provider,
        String locationLabel,
        WeatherLocationSource locationSource,
        int nx,
        int ny,
        String baseDate,
        String baseTime,
        String forecastDate,
        String forecastTime,
        Double temperatureC,
        Double precipitationMm,
        String precipitationType,
        String sky,
        Double windSpeedMs,
        List<String> summaryTraits,
        String fetchedAt
) {
}
