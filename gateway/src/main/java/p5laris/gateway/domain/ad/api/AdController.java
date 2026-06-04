package p5laris.gateway.domain.ad.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.ad.api.dto.AdDto;
import p5laris.gateway.domain.ad.application.AdConfigService;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/ad")
@RequiredArgsConstructor
public class AdController {

    private final AdConfigService adConfigService;

    @GetMapping("/v1/banner-config")
    public ApiResponse<AdDto.BannerConfigResponse> getBannerConfig(
            @RequestParam String placement,
            @RequestParam(required = false) String path
    ) {
        return ApiResponse.success(adConfigService.getBannerConfig(placement, path));
    }
}
