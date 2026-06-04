package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;

public class AuthResponse {
    @Getter
    @Builder
    public static class GoogleAuthUrl {
        private String authorizationUrl;
        private String state;
    }

    @Getter
    @Builder
    public static class LoginGoogle {
        private String accessToken;
        private String refreshToken;
        private UserDto user;
    }

    @Getter
    @Builder
    public static class UserDto {
        private Long id;
        private String email;
        private String nickname;
        private String provider;
        private String role;
    }

    @Getter
    @Builder
    public static class RefreshToken {
        private String accessToken;
        private String refreshToken;
    }

    @Getter
    @Builder
    public static class Logout {
        private boolean loggedOut;
    }
}
