package p5laris.mission.domain.application.weather;

public interface WeatherCache {

    WeatherCacheLookup get(String cacheKey);

    void put(String cacheKey, WeatherSnapshot snapshot);
}
