package p5laris.gateway.domain.user.api;

import com.p5laris.proto.user.v1.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.domain.user.api.dto.AuthRequest;
import p5laris.gateway.domain.user.api.dto.AuthResponse;
import p5laris.gateway.domain.user.infrastructure.grpc.AuthGatewayService;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthGatewayService authGatewayService;

    /**
     * 구글 로그인 URL 얻기
     * @param request
     * @return
     */
    @GetMapping("/v1/google/authorization-url")
    public ApiResponse<AuthResponse.GoogleAuthUrl> getAuthorizationUrl(@Valid @ModelAttribute AuthRequest.GoogleAuthUrl request) {
        return ApiResponse.success(authGatewayService.getAuthorizationUrl(request));
    }

    /**
     * 구글 세션 생성 (로그인 처리 및 JWT 발급)
     * @param request
     * @return
     */
    @PostMapping("/v1/google/sessions")
    public ApiResponse<AuthResponse.LoginGoogle> loginGoogle(@Valid @RequestBody AuthRequest.LoginGoogle request) {
        return ApiResponse.success(authGatewayService.loginGoogle(request));
    }

    /**
     * 토큰 재발급
     * @param request
     * @return
     */
    @PostMapping("/v1/token-refreshes")
    public ApiResponse<AuthResponse.RefreshToken> refreshToken(@Valid @RequestBody AuthRequest.RefreshToken request) {
        return ApiResponse.success(authGatewayService.refreshToken(request));
    }

    /**
     * 로그아웃
     * @param userId
     * @return
     */
    @DeleteMapping("/v1/sessions/current")
    public ApiResponse<AuthResponse.Logout> logout(
            @LoginUserId Long userId,
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.substring(7);
        return ApiResponse.success(authGatewayService.logout(userId, token));
    }
}
