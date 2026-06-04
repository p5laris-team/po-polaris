package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.api.dto.WeatherRegionDto;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserGatewayService userGatewayService;

    /**
     * 내 정보 조회
     * @param userId
     * @return
     */
    @GetMapping("/v1/users/me")
    public ApiResponse<UserDto> getMe(@LoginUserId Long userId) {
        return ApiResponse.success(userGatewayService.getUser(userId));
    }

    /**
     * 사용자가 직접 선택할 수 있는 날씨 권역 목록 조회
     */
    @GetMapping("/v1/weather-regions")
    public ApiResponse<WeatherRegionDto.RegionListResponse> listWeatherRegions() {
        return ApiResponse.success(userGatewayService.listWeatherRegions());
    }

    /**
     * 내 날씨 권역 조회
     */
    @GetMapping("/v1/users/me/weather-region")
    public ApiResponse<WeatherRegionDto.SelectedRegionResponse> getWeatherRegion(@LoginUserId Long userId) {
        return ApiResponse.success(userGatewayService.getWeatherRegion(userId));
    }

    /**
     * 내 날씨 권역 저장 및 수정
     */
    @PutMapping("/v1/users/me/weather-region")
    public ApiResponse<WeatherRegionDto.SelectedRegionResponse> updateWeatherRegion(
            @LoginUserId Long userId,
            @RequestBody WeatherRegionDto.UpdateRegionRequest request
    ) {
        return ApiResponse.success(userGatewayService.updateWeatherRegion(userId, request));
    }
}
