package p5laris.mission.domain.application.weather;

public enum WeatherFailureReason {
    DISABLED,
    UNSUPPORTED_PROVIDER,
    INVALID_CONFIG,
    CACHE_UNAVAILABLE,
    RATE_LIMIT,
    RATE_LIMIT_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    PROVIDER_ERROR,
    INVALID_RESPONSE
}
