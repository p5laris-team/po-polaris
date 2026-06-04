package p5laris.mission.domain.infrastructure.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.weather.WeatherFailureReason;
import p5laris.mission.domain.application.weather.WeatherLocation;
import p5laris.mission.domain.application.weather.WeatherProvider;
import p5laris.mission.domain.application.weather.WeatherProviderException;
import p5laris.mission.domain.application.weather.WeatherSnapshot;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class KmaWeatherProvider implements WeatherProvider {

    private static final String PROVIDER_NAME = "KMA";
    private static final DateTimeFormatter FETCHED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MissionWeatherProperties properties;
    private final KmaUltraSrtBaseTimeResolver baseTimeResolver;
    private final KmaWeatherResponseParser responseParser;
    private final Clock clock;

    @Override
    public boolean supports(String provider) {
        return "kma".equalsIgnoreCase(provider);
    }

    @Override
    public String sourceName() {
        return PROVIDER_NAME;
    }

    @Override
    public String cacheKey(WeatherLocation location, LocalDateTime now) {
        KmaBaseTime baseTime = baseTimeResolver.resolve(now);
        return "mission:weather:kma:v1:%d:%d:%s:%s".formatted(
                location.nx(),
                location.ny(),
                baseTime.baseDate(),
                baseTime.baseTime()
        );
    }

    @Override
    public WeatherSnapshot fetch(WeatherLocation location, LocalDateTime now) {
        if (!properties.hasKmaConfig()) {
            throw new WeatherProviderException(
                    WeatherFailureReason.INVALID_CONFIG,
                    "기상청 날씨 API 설정이 비어 있습니다."
            );
        }

        KmaBaseTime baseTime = baseTimeResolver.resolve(now);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl(location, baseTime)))
                .timeout(properties.timeout())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeatherProviderException(
                        WeatherFailureReason.PROVIDER_ERROR,
                        "기상청 날씨 API HTTP 응답이 성공이 아닙니다. status=" + response.statusCode()
                );
            }

            return responseParser.parse(
                    response.body(),
                    location,
                    baseTime,
                    now,
                    FETCHED_AT_FORMATTER.format(LocalDateTime.now(clock))
            );
        } catch (HttpTimeoutException e) {
            throw new WeatherProviderException(
                    WeatherFailureReason.PROVIDER_TIMEOUT,
                    "기상청 날씨 API 호출 시간이 초과되었습니다.",
                    e
            );
        } catch (IOException e) {
            throw new WeatherProviderException(
                    WeatherFailureReason.PROVIDER_ERROR,
                    "기상청 날씨 API 호출에 실패했습니다.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherProviderException(
                    WeatherFailureReason.PROVIDER_ERROR,
                    "기상청 날씨 API 호출이 중단되었습니다.",
                    e
            );
        }
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }

    private String requestUrl(WeatherLocation location, KmaBaseTime baseTime) {
        String baseUrl = properties.getKma().getBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/getUltraSrtFcst"
                + "?serviceKey=" + encode(properties.getKma().getServiceKey())
                + "&pageNo=1"
                + "&numOfRows=1000"
                + "&dataType=JSON"
                + "&base_date=" + baseTime.baseDate()
                + "&base_time=" + baseTime.baseTime()
                + "&nx=" + location.nx()
                + "&ny=" + location.ny();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
