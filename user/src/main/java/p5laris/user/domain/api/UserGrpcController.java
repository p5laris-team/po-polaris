package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.AuthService;
import p5laris.user.domain.application.UserService;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserGrpcController extends UserServiceGrpc.UserServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "사용자 서비스 처리 중 오류가 발생했습니다.";
    private static final String AUTHENTICATION_ERROR_DESCRIPTION = "인증 처리에 실패했습니다.";

    private final AuthService authService;
    private final UserService userService;

    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getGoogleAuthUrl(GetGoogleAuthUrlRequest request, StreamObserver<GetGoogleAuthUrlResponse> responseObserver) {
        try {
            String state = "oauth-state-token";
            String url = authService.getGoogleAuthUrl(request.getRedirectUri(), state);
            
            GetGoogleAuthUrlResponse response = GetGoogleAuthUrlResponse.newBuilder()
                    .setAuthorizationUrl(url)
                    .setState(state)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("구글 인증 URL 생성", e));
        }
    }

    @Override
    public void loginGoogle(LoginGoogleRequest request, StreamObserver<LoginGoogleResponse> responseObserver) {
        try {
            AuthService.LoginResult result = authService.loginGoogle(request.getCode(), request.getRedirectUri(), request.getClientId());
            
            User protoUser = User.newBuilder()
                    .setId(result.user().getId())
                    .setEmail(result.user().getEmail())
                    .setNickname(result.user().getNickname())
                    .setProvider(result.user().getProvider())
                    .setRole(result.user().getRole())
                    .setStatus(result.user().getStatus())
                    .build();

            LoginGoogleResponse response = LoginGoogleResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .setUser(protoUser)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn("사용자 인증 gRPC 처리 실패. operation={}", "구글 로그인", e);
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription(AUTHENTICATION_ERROR_DESCRIPTION)
                    .asRuntimeException());
        }
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<RefreshTokenResponse> responseObserver) {
        try {
            AuthService.RefreshResult result = authService.refreshToken(request.getRefreshToken());
            
            RefreshTokenResponse response = RefreshTokenResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn("사용자 인증 gRPC 처리 실패. operation={}", "토큰 재발급", e);
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription(AUTHENTICATION_ERROR_DESCRIPTION)
                    .asRuntimeException());
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            authService.logout(request.getUserId(), request.getAccessToken());
            
            LogoutResponse response = LogoutResponse.newBuilder()
                    .setLoggedOut(true)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("로그아웃", e));
        }
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
        try {
            User protoUser = userService.getUser(request.getUserId());

            GetUserResponse response = GetUserResponse.newBuilder()
                    .setUser(protoUser)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn("사용자 조회 gRPC 처리 실패. operation={}", "사용자 조회", e);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(UserErrorCode.USER_NOT_FOUND.getCode())
                    .asRuntimeException());
        }
    }

    @Override
    public void listWeatherRegions(ListWeatherRegionsRequest request, StreamObserver<ListWeatherRegionsResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.listWeatherRegions());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("날씨 권역 목록 조회", e));
        }
    }

    @Override
    public void getWeatherRegion(GetWeatherRegionRequest request, StreamObserver<GetWeatherRegionResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.getWeatherRegion(request.getUserId()));
            responseObserver.onCompleted();
        } catch (UserException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("사용자 날씨 권역 조회", e));
        }
    }

    @Override
    public void updateWeatherRegion(UpdateWeatherRegionRequest request, StreamObserver<UpdateWeatherRegionResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.updateWeatherRegion(request.getUserId(), request.getRegionCode()));
            responseObserver.onCompleted();
        } catch (UserException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("사용자 날씨 권역 변경", e));
        }
    }

    private String safeDescription(UserException e) {
        return e.getErrorCode().getCode();
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("사용자 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }

    private Status toStatus(UserException e) {
        if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
            return Status.NOT_FOUND;
        }
        if (e.getErrorCode() == UserErrorCode.INVALID_WEATHER_REGION) {
            return Status.INVALID_ARGUMENT;
        }
        return Status.INTERNAL;
    }
}
