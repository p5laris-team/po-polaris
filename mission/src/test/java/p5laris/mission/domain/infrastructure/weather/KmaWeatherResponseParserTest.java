package p5laris.mission.domain.infrastructure.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import p5laris.mission.domain.application.weather.WeatherLocation;
import p5laris.mission.domain.application.weather.WeatherLocationSource;
import p5laris.mission.domain.application.weather.WeatherSnapshot;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaWeatherResponseParserTest {

    private final KmaWeatherResponseParser parser = new KmaWeatherResponseParser(new ObjectMapper());

    @Test
    void 가장_가까운_미래_예보_시각을_선택해_날씨_요약을_만든다() {
        WeatherSnapshot snapshot = parser.parse(
                responseBody(),
                new WeatherLocation(60, 127, "SEOUL", WeatherLocationSource.SERVICE_DEFAULT),
                new KmaBaseTime("20260601", "1230"),
                LocalDateTime.of(2026, 6, 1, 12, 40),
                "2026-06-01T12:45:00"
        );

        assertThat(snapshot.provider()).isEqualTo("KMA");
        assertThat(snapshot.forecastDate()).isEqualTo("20260601");
        assertThat(snapshot.forecastTime()).isEqualTo("1300");
        assertThat(snapshot.temperatureC()).isEqualTo(29.0d);
        assertThat(snapshot.precipitationMm()).isEqualTo(1.0d);
        assertThat(snapshot.precipitationType()).isEqualTo("RAIN");
        assertThat(snapshot.sky()).isEqualTo("OVERCAST");
        assertThat(snapshot.windSpeedMs()).isEqualTo(8.5d);
        assertThat(snapshot.summaryTraits()).contains("RAINY", "HOT", "WINDY", "CLOUDY");
    }

    private String responseBody() {
        return """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL_SERVICE"
                    },
                    "body": {
                      "items": {
                        "item": [
                          {"category": "T1H", "fcstDate": "20260601", "fcstTime": "1300", "fcstValue": "29"},
                          {"category": "RN1", "fcstDate": "20260601", "fcstTime": "1300", "fcstValue": "1.0mm"},
                          {"category": "PTY", "fcstDate": "20260601", "fcstTime": "1300", "fcstValue": "1"},
                          {"category": "SKY", "fcstDate": "20260601", "fcstTime": "1300", "fcstValue": "4"},
                          {"category": "WSD", "fcstDate": "20260601", "fcstTime": "1300", "fcstValue": "8.5"},
                          {"category": "T1H", "fcstDate": "20260601", "fcstTime": "1400", "fcstValue": "30"}
                        ]
                      }
                    }
                  }
                }
                """;
    }
}
