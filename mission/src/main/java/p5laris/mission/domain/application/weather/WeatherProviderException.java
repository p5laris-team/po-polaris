package p5laris.mission.domain.application.weather;

public class WeatherProviderException extends RuntimeException {

    private final WeatherFailureReason failureReason;

    public WeatherProviderException(WeatherFailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public WeatherProviderException(WeatherFailureReason failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public WeatherFailureReason failureReason() {
        return failureReason;
    }
}
