package p5laris.user.domain.application;

import com.p5laris.proto.user.v1.User;
import com.p5laris.proto.user.v1.WeatherRegion;
import com.p5laris.proto.user.v1.GetWeatherRegionResponse;
import com.p5laris.proto.user.v1.ListWeatherRegionsResponse;
import com.p5laris.proto.user.v1.UpdateWeatherRegionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.enums.WeatherRegionCode;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getUser(Long userId) {
        p5laris.user.domain.domain.entity.User user = findUser(userId);

        return User.newBuilder()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setNickname(user.getNickname())
                .setProvider(user.getProvider())
                .setRole(user.getRole())
                .setStatus(user.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public ListWeatherRegionsResponse listWeatherRegions() {
        return ListWeatherRegionsResponse.newBuilder()
                .addAllRegions(Arrays.stream(WeatherRegionCode.values())
                        .map(this::toProtoWeatherRegion)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public GetWeatherRegionResponse getWeatherRegion(Long userId) {
        p5laris.user.domain.domain.entity.User user = findUser(userId);

        return WeatherRegionCode.fromCode(user.getWeatherRegionCode())
                .map(region -> GetWeatherRegionResponse.newBuilder()
                        .setSelected(true)
                        .setRegion(toProtoWeatherRegion(region))
                        .build())
                .orElseGet(() -> GetWeatherRegionResponse.newBuilder()
                        .setSelected(false)
                        .build());
    }

    @Transactional
    public UpdateWeatherRegionResponse updateWeatherRegion(Long userId, String regionCode) {
        WeatherRegionCode region = WeatherRegionCode.fromCode(regionCode)
                .orElseThrow(() -> new UserException(UserErrorCode.INVALID_WEATHER_REGION));

        p5laris.user.domain.domain.entity.User user = findUser(userId);
        user.updateWeatherRegionCode(region.code());

        return UpdateWeatherRegionResponse.newBuilder()
                .setSelected(true)
                .setRegion(toProtoWeatherRegion(region))
                .build();
    }

    private p5laris.user.domain.domain.entity.User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private WeatherRegion toProtoWeatherRegion(WeatherRegionCode region) {
        return WeatherRegion.newBuilder()
                .setCode(region.code())
                .setDisplayName(region.displayName())
                .setNx(region.nx())
                .setNy(region.ny())
                .build();
    }
}
