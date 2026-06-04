package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.*;
import jakarta.validation.Valid;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import p5laris.gateway.domain.user.api.dto.AuthRequest;
import p5laris.gateway.domain.user.api.dto.AuthResponse;
import p5laris.gateway.global.auth.LoginUserId;

@Service
public class AuthGatewayService {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    // 구글 로그인 URL 얻기
    public AuthResponse.GoogleAuthUrl getAuthorizationUrl(@Valid @ModelAttribute AuthRequest.GoogleAuthUrl request) {
        GetGoogleAuthUrlRequest grpcRequest = GetGoogleAuthUrlRequest.newBuilder()
                .setRedirectUri(request.getRedirectUri())
                .build();

        GetGoogleAuthUrlResponse grpcResponse = userServiceStub.getGoogleAuthUrl(grpcRequest);

        return AuthResponse.GoogleAuthUrl.builder()
                .authorizationUrl(grpcResponse.getAuthorizationUrl())
                .state(grpcResponse.getState())
                .build();
    }

    // 구글 세션 생성 (로그인 처리 및 JWT 발급)
    public AuthResponse.LoginGoogle loginGoogle(@Valid @RequestBody AuthRequest.LoginGoogle request) {
        LoginGoogleRequest grpcRequest = LoginGoogleRequest.newBuilder()
                .setCode(request.getCode())
                .setState(request.getState())
                .setRedirectUri(request.getRedirectUri())
                .setClientId(request.getClientId() != null ? request.getClientId() : "")
                .build();

        LoginGoogleResponse grpcResponse = userServiceStub.loginGoogle(grpcRequest);

        User protoUser = grpcResponse.getUser();
        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(protoUser.getId())
                .email(protoUser.getEmail())
                .nickname(protoUser.getNickname())
                .provider(protoUser.getProvider())
                .role(protoUser.getRole())
                .build();

        return AuthResponse.LoginGoogle.builder()
                .accessToken(grpcResponse.getAccessToken())
                .refreshToken(grpcResponse.getRefreshToken())
                .user(userDto)
                .build();
    }

    // 토큰 재발급
    public AuthResponse.RefreshToken refreshToken(@Valid @RequestBody AuthRequest.RefreshToken request) {
        RefreshTokenRequest grpcRequest = RefreshTokenRequest.newBuilder()
                .setRefreshToken(request.getRefreshToken())
                .build();

        RefreshTokenResponse grpcResponse = userServiceStub.refreshToken(grpcRequest);

        return AuthResponse.RefreshToken.builder()
                .accessToken(grpcResponse.getAccessToken())
                .refreshToken(grpcResponse.getRefreshToken())
                .build();
    }

    // 로그아웃
    public AuthResponse.Logout logout(Long userId, String token) {
        LogoutRequest grpcRequest = LogoutRequest.newBuilder()
                .setUserId(userId)
                .setAccessToken(token)
                .build();

        LogoutResponse grpcResponse = userServiceStub.logout(grpcRequest);

        return AuthResponse.Logout.builder()
                .loggedOut(grpcResponse.getLoggedOut())
                .build();
    }
}
