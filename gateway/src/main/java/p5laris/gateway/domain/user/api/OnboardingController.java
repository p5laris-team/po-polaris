package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.domain.user.api.dto.OnboardingDto;
import p5laris.gateway.domain.user.infrastructure.grpc.OnboardingGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingGatewayService onboardingGatewayService;

    /**
     * 온보딩 질문 목록 조회
     * @return
     */
    @GetMapping("/v1/questions")
    public ApiResponse<List<OnboardingDto.QuestionResponse>> getQuestions() {
        return ApiResponse.success(onboardingGatewayService.getQuestions());
    }

    /**
     * 내 온보딩 프로필 조회
     * @param userId
     * @return
     */
    @GetMapping("/v1/profiles/me")
    public ApiResponse<OnboardingDto.ProfileResponse> getProfile(@LoginUserId Long userId) {
        return ApiResponse.success(onboardingGatewayService.getProfile(userId));
    }

    /**
     * 내 온보딩 프로필 저장
     * @param userId
     * @param request
     * @return
     */
    @PutMapping("/v1/profiles/me")
    public ApiResponse<OnboardingDto.ProfileResponse> saveProfile(
            @LoginUserId Long userId,
            @RequestBody OnboardingDto.SaveProfileRequest request) {
        return ApiResponse.success(onboardingGatewayService.saveProfile(userId, request));
    }
}
