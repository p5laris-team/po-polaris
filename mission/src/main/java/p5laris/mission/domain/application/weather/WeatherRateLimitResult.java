package p5laris.mission.domain.application.weather;

public record WeatherRateLimitResult(
        boolean allowed,
        WeatherFailureReason failureReason
) {

    public static WeatherRateLimitResult allow() {
        return new WeatherRateLimitResult(true, null);
    }

    public static WeatherRateLimitResult denied(WeatherFailureReason failureReason) {
        return new WeatherRateLimitResult(false, failureReason);
    }
}
