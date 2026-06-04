package p5laris.mission.domain.application.weather;

public interface WeatherRateLimiter {

    WeatherRateLimitResult tryAcquire(String provider);
}
