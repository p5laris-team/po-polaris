package p5laris.mission.domain.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.application.weather.WeatherFailureReason;
import p5laris.mission.domain.application.weather.WeatherLocation;
import p5laris.mission.domain.application.weather.WeatherProviderException;
import p5laris.mission.domain.application.weather.WeatherSnapshot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class KmaWeatherResponseParser {

    private static final String SUCCESS_CODE = "00";
    private static final DateTimeFormatter KMA_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ObjectMapper objectMapper;

    public WeatherSnapshot parse(
            String body,
            WeatherLocation location,
            KmaBaseTime baseTime,
            LocalDateTime now,
            String fetchedAt
    ) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root.path("response");
            JsonNode header = response.path("header");
            String resultCode = header.path("resultCode").asText();
            if (!SUCCESS_CODE.equals(resultCode)) {
                throw new WeatherProviderException(
                        WeatherFailureReason.PROVIDER_ERROR,
                        "기상청 초단기예보 응답 resultCode가 성공이 아닙니다. resultCode=" + resultCode
                );
            }

            JsonNode items = response.path("body").path("items").path("item");
            if (!items.isArray() || items.isEmpty()) {
                throw new WeatherProviderException(
                        WeatherFailureReason.INVALID_RESPONSE,
                        "기상청 초단기예보 응답 item이 비어 있습니다."
                );
            }

            List<KmaForecastItem> forecastItems = readItems(items);
            LocalDateTime forecastDateTime = selectForecastDateTime(forecastItems, now)
                    .orElseThrow(() -> new WeatherProviderException(
                            WeatherFailureReason.INVALID_RESPONSE,
                            "기상청 초단기예보 forecast 시각을 선택할 수 없습니다."
                    ));
            Map<String, String> valueByCategory = valuesAt(forecastItems, forecastDateTime);

            Double temperatureC = parseNumber(valueByCategory.get("T1H"));
            Double precipitationMm = parsePrecipitation(valueByCategory.get("RN1"));
            String precipitationType = precipitationType(valueByCategory.get("PTY"));
            String sky = sky(valueByCategory.get("SKY"));
            Double windSpeedMs = parseNumber(valueByCategory.get("WSD"));
            List<String> summaryTraits = summaryTraits(temperatureC, precipitationMm, precipitationType, sky, windSpeedMs);

            return new WeatherSnapshot(
                    "KMA",
                    location.locationLabel(),
                    location.source(),
                    location.nx(),
                    location.ny(),
                    baseTime.baseDate(),
                    baseTime.baseTime(),
                    forecastDateTime.format(DateTimeFormatter.BASIC_ISO_DATE),
                    forecastDateTime.format(DateTimeFormatter.ofPattern("HHmm")),
                    temperatureC,
                    precipitationMm,
                    precipitationType,
                    sky,
                    windSpeedMs,
                    summaryTraits,
                    fetchedAt
            );
        } catch (WeatherProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new WeatherProviderException(
                    WeatherFailureReason.INVALID_RESPONSE,
                    "기상청 초단기예보 응답 파싱에 실패했습니다.",
                    e
            );
        }
    }

    private List<KmaForecastItem> readItems(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false)
                .map(this::toForecastItem)
                .filter(Objects::nonNull)
                .toList();
    }

    private KmaForecastItem toForecastItem(JsonNode node) {
        String category = node.path("category").asText();
        String value = node.path("fcstValue").asText();
        String forecastDate = node.path("fcstDate").asText();
        String forecastTime = node.path("fcstTime").asText();
        if (category.isBlank() || forecastDate.isBlank() || forecastTime.isBlank()) {
            return null;
        }

        try {
            return new KmaForecastItem(
                    category,
                    value,
                    LocalDateTime.parse(forecastDate + forecastTime, KMA_DATE_TIME_FORMATTER)
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Optional<LocalDateTime> selectForecastDateTime(List<KmaForecastItem> items, LocalDateTime now) {
        return items.stream()
                .map(KmaForecastItem::forecastDateTime)
                .filter(Objects::nonNull)
                .filter(forecastDateTime -> !forecastDateTime.isBefore(now))
                .min(Comparator.naturalOrder())
                .or(() -> items.stream()
                        .map(KmaForecastItem::forecastDateTime)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()));
    }

    private Map<String, String> valuesAt(List<KmaForecastItem> items, LocalDateTime forecastDateTime) {
        Map<String, String> valueByCategory = new LinkedHashMap<>();
        items.stream()
                .filter(item -> forecastDateTime.equals(item.forecastDateTime()))
                .forEach(item -> valueByCategory.put(item.category(), item.value()));
        return valueByCategory;
    }

    private Double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) {
            return null;
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parsePrecipitation(String value) {
        if (value == null || value.isBlank() || value.contains("강수없음")) {
            return 0.0d;
        }
        if (value.contains("미만")) {
            return 0.5d;
        }
        return parseNumber(value);
    }

    private String precipitationType(String value) {
        return switch (value == null ? "" : value.trim()) {
            case "0" -> "NONE";
            case "1" -> "RAIN";
            case "2" -> "RAIN_SNOW";
            case "3" -> "SNOW";
            case "5" -> "RAINDROP";
            case "6" -> "RAINDROP_SNOW";
            case "7" -> "SNOW_FLURRY";
            default -> "UNKNOWN";
        };
    }

    private String sky(String value) {
        return switch (value == null ? "" : value.trim()) {
            case "1" -> "CLEAR";
            case "3" -> "CLOUDY";
            case "4" -> "OVERCAST";
            default -> "UNKNOWN";
        };
    }

    private List<String> summaryTraits(
            Double temperatureC,
            Double precipitationMm,
            String precipitationType,
            String sky,
            Double windSpeedMs
    ) {
        Set<String> traits = new LinkedHashSet<>();
        if (precipitationType != null && !"NONE".equals(precipitationType) && !"UNKNOWN".equals(precipitationType)) {
            traits.add(precipitationType.contains("SNOW") ? "SNOWY" : "RAINY");
        }
        if (precipitationMm != null && precipitationMm > 0) {
            traits.add("RAINY");
        }
        if (temperatureC != null && temperatureC >= 28) {
            traits.add("HOT");
        }
        if (temperatureC != null && temperatureC <= 5) {
            traits.add("COLD");
        }
        if (windSpeedMs != null && windSpeedMs >= 8) {
            traits.add("WINDY");
        }
        if ("CLEAR".equals(sky) && traits.stream().noneMatch(trait -> trait.equals("RAINY") || trait.equals("SNOWY"))) {
            traits.add("CLEAR");
        }
        if ("CLOUDY".equals(sky) || "OVERCAST".equals(sky)) {
            traits.add("CLOUDY");
        }
        if (traits.isEmpty()) {
            traits.add("STABLE");
        }
        return List.copyOf(traits);
    }

    private record KmaForecastItem(
            String category,
            String value,
            LocalDateTime forecastDateTime
    ) {
    }
}
