package p5laris.gateway.domain.home.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.home.api.dto.HomeDto;
import p5laris.gateway.domain.home.infrastructure.grpc.HomeGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeGatewayService homeGatewayService;

    /**
     * 홈 화면에 필요한 통합 정보를 한 번에 조회한다.
     *
     * @param userId 로그인한 사용자 ID
     * @return 통합 홈 데이터 응답
     */
    @GetMapping("/v1/home")
    public ApiResponse<HomeDto.Response> getHomeData(@LoginUserId Long userId) {
        HomeDto.Response response = homeGatewayService.getHomeData(userId);
        return ApiResponse.success(response);
    }
}
