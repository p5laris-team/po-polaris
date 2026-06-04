package p5laris.user.domain.domain.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 사용자가 직접 고르는 날씨 권역이다.
 *
 * <p>상세 주소나 GPS를 받지 않고도 17개 시도보다 날씨 오차를 줄이기 위해
 * 생활권 단위 대표 좌표를 기상청 격자 좌표로 변환해 사용한다.</p>
 */
public enum WeatherRegionCode {

    SEOUL("서울", 37.5665, 126.9780),
    BUSAN("부산", 35.1796, 129.0756),
    DAEGU("대구", 35.8714, 128.6014),
    INCHEON("인천", 37.4563, 126.7052),
    GWANGJU("광주", 35.1595, 126.8526),
    DAEJEON("대전", 36.3504, 127.3845),
    ULSAN("울산", 35.5384, 129.3114),
    SEJONG("세종", 36.4800, 127.2890),

    GYEONGGI_NORTH("경기 북부", 37.7381, 127.0337),
    GYEONGGI_SOUTH("경기 남부", 37.2636, 127.0286),
    GYEONGGI_EAST("경기 동부", 37.2980, 127.6371),
    GYEONGGI_WEST("경기 서부", 37.6152, 126.7156),

    GANGWON_YEONGSEO_NORTH("강원 영서 북부", 37.8813, 127.7298),
    GANGWON_YEONGSEO_SOUTH("강원 영서 남부", 37.3422, 127.9202),
    GANGWON_YEONGDONG_NORTH("강원 영동 북부", 38.2070, 128.5918),
    GANGWON_YEONGDONG_SOUTH("강원 영동 남부", 37.7519, 128.8761),

    CHUNGBUK_NORTH("충북 북부", 36.9910, 127.9259),
    CHUNGBUK_CENTRAL("충북 중부", 36.6424, 127.4890),
    CHUNGBUK_SOUTH("충북 남부", 36.1748, 127.7766),

    CHUNGNAM_NORTH("충남 북부", 36.8151, 127.1139),
    CHUNGNAM_INLAND("충남 내륙", 36.4465, 127.1190),
    CHUNGNAM_WEST_COAST("충남 서해안", 36.7845, 126.4503),
    CHUNGNAM_SOUTH("충남 남부", 36.1871, 127.0987),

    JEONBUK_NORTH("전북 북부", 35.8242, 127.1480),
    JEONBUK_WEST_COAST("전북 서해안", 35.9676, 126.7368),
    JEONBUK_EAST("전북 동부", 36.0068, 127.6608),
    JEONBUK_SOUTH("전북 남부", 35.4164, 127.3904),

    JEONNAM_WEST("전남 서부", 34.8118, 126.3922),
    JEONNAM_INLAND("전남 내륙", 35.0161, 126.7108),
    JEONNAM_EAST("전남 동부", 34.9506, 127.4872),
    JEONNAM_SOUTH_COAST("전남 남해안", 34.7604, 127.6622),

    GYEONGBUK_NORTH("경북 북부", 36.5684, 128.7294),
    GYEONGBUK_SOUTH("경북 남부", 36.1195, 128.3446),
    GYEONGBUK_EAST_COAST("경북 동해안", 36.0190, 129.3435),
    GYEONGBUK_NORTHEAST("경북 북동부", 36.8057, 128.6241),

    GYEONGNAM_WEST("경남 서부", 35.1800, 128.1076),
    GYEONGNAM_EAST("경남 동부", 35.2285, 128.8894),
    GYEONGNAM_SOUTH_COAST("경남 남해안", 34.8544, 128.4332),
    GYEONGNAM_NORTH("경남 북부", 35.6866, 127.9095),

    JEJU_NORTH("제주 북부", 33.4996, 126.5312),
    JEJU_SOUTH("제주 남부", 33.2541, 126.5601),
    JEJU_EAST("제주 동부", 33.4507, 126.9180),
    JEJU_WEST("제주 서부", 33.3930, 126.2630);

    private final String displayName;
    private final int nx;
    private final int ny;

    WeatherRegionCode(String displayName, double latitude, double longitude) {
        Grid grid = Grid.from(latitude, longitude);
        this.displayName = displayName;
        this.nx = grid.nx();
        this.ny = grid.ny();
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public int nx() {
        return nx;
    }

    public int ny() {
        return ny;
    }

    public static Optional<WeatherRegionCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(region -> region.name().equals(normalized))
                .findFirst();
    }

    /**
     * 기상청 단기예보 격자 변환식이다. 권역 목록은 사람이 읽기 쉬운 위경도로 관리하고,
     * API 호출에는 이 변환 결과인 nx/ny만 사용한다.
     */
    private record Grid(int nx, int ny) {
        private static final double EARTH_RADIUS_KM = 6371.00877;
        private static final double GRID_KM = 5.0;
        private static final double STANDARD_LATITUDE_1 = 30.0;
        private static final double STANDARD_LATITUDE_2 = 60.0;
        private static final double ORIGIN_LONGITUDE = 126.0;
        private static final double ORIGIN_LATITUDE = 38.0;
        private static final double ORIGIN_X = 43.0;
        private static final double ORIGIN_Y = 136.0;

        private static Grid from(double latitude, double longitude) {
            double degrad = Math.PI / 180.0;
            double re = EARTH_RADIUS_KM / GRID_KM;
            double slat1 = STANDARD_LATITUDE_1 * degrad;
            double slat2 = STANDARD_LATITUDE_2 * degrad;
            double olon = ORIGIN_LONGITUDE * degrad;
            double olat = ORIGIN_LATITUDE * degrad;

            double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                    / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
            sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);

            double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
            sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;

            double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
            ro = re * sf / Math.pow(ro, sn);

            double ra = Math.tan(Math.PI * 0.25 + latitude * degrad * 0.5);
            ra = re * sf / Math.pow(ra, sn);

            double theta = longitude * degrad - olon;
            if (theta > Math.PI) {
                theta -= 2.0 * Math.PI;
            }
            if (theta < -Math.PI) {
                theta += 2.0 * Math.PI;
            }
            theta *= sn;

            int nx = (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
            int ny = (int) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
            return new Grid(nx, ny);
        }
    }
}
