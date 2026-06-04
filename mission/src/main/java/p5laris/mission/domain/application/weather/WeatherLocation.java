package p5laris.mission.domain.application.weather;

public record WeatherLocation(
        int nx,
        int ny,
        String locationLabel,
        WeatherLocationSource source
) {
}
