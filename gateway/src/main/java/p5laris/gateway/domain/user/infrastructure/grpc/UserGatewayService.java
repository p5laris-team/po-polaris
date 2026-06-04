package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetUserRequest;
import com.p5laris.proto.user.v1.GetUserResponse;
import com.p5laris.proto.user.v1.GetWeatherRegionRequest;
import com.p5laris.proto.user.v1.GetWeatherRegionResponse;
import com.p5laris.proto.user.v1.ListWeatherRegionsRequest;
import com.p5laris.proto.user.v1.ListWeatherRegionsResponse;
import com.p5laris.proto.user.v1.UpdateWeatherRegionRequest;
import com.p5laris.proto.user.v1.UpdateWeatherRegionResponse;
import com.p5laris.proto.user.v1.User;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import com.p5laris.proto.user.v1.WeatherRegion;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.api.dto.WeatherRegionDto;
import p5laris.gateway.domain.user.exception.UserGatewayErrorCode;
import p5laris.gateway.domain.user.exception.UserGatewayException;

import java.util.List;

@Service
public class UserGatewayService {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    // 내 정보 조회
    public UserDto getUser(Long userId) {
        GetUserResponse response;
        try {
            response = userServiceStub.getUser(
                    GetUserRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }

        User protoUser = response.getUser();
        return UserDto.builder()
                .id(protoUser.getId())
                .email(protoUser.getEmail())
                .nickname(protoUser.getNickname())
                .provider(protoUser.getProvider())
                .role(protoUser.getRole())
                .status(protoUser.getStatus())
                .build();
    }

    public WeatherRegionDto.RegionListResponse listWeatherRegions() {
        ListWeatherRegionsResponse response;
        try {
            response = userServiceStub.listWeatherRegions(
                    ListWeatherRegionsRequest.newBuilder().build()
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }

        List<WeatherRegionDto.RegionItem> regions = response.getRegionsList().stream()
                .map(this::toRegionItem)
                .toList();
        return new WeatherRegionDto.RegionListResponse(regions);
    }

    public WeatherRegionDto.SelectedRegionResponse getWeatherRegion(Long userId) {
        try {
            GetWeatherRegionResponse response = userServiceStub.getWeatherRegion(
                    GetWeatherRegionRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            return toSelectedRegionResponse(response.getSelected(), response.hasRegion() ? response.getRegion() : null);
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    public WeatherRegionDto.SelectedRegionResponse updateWeatherRegion(
            Long userId,
            WeatherRegionDto.UpdateRegionRequest request
    ) {
        if (request == null || request.regionCode() == null || request.regionCode().isBlank()) {
            throw new UserGatewayException(UserGatewayErrorCode.INVALID_WEATHER_REGION);
        }

        try {
            UpdateWeatherRegionResponse response = userServiceStub.updateWeatherRegion(
                    UpdateWeatherRegionRequest.newBuilder()
                            .setUserId(userId)
                            .setRegionCode(request.regionCode())
                            .build()
            );

            return toSelectedRegionResponse(response.getSelected(), response.hasRegion() ? response.getRegion() : null);
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    private WeatherRegionDto.RegionItem toRegionItem(WeatherRegion region) {
        return new WeatherRegionDto.RegionItem(
                region.getCode(),
                region.getDisplayName()
        );
    }

    private WeatherRegionDto.SelectedRegionResponse toSelectedRegionResponse(boolean selected, WeatherRegion region) {
        if (!selected || region == null) {
            return new WeatherRegionDto.SelectedRegionResponse(false, null, null);
        }

        return new WeatherRegionDto.SelectedRegionResponse(
                true,
                region.getCode(),
                region.getDisplayName()
        );
    }

    private UserGatewayException toGatewayException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();

        if (code == Status.Code.NOT_FOUND) {
            return new UserGatewayException(UserGatewayErrorCode.USER_NOT_FOUND);
        }
        if (code == Status.Code.INVALID_ARGUMENT) {
            return new UserGatewayException(UserGatewayErrorCode.INVALID_WEATHER_REGION);
        }
        return new UserGatewayException(UserGatewayErrorCode.USER_SERVICE_UNAVAILABLE);
    }
}
