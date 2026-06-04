package p5laris.gateway.domain.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

public class AuthRequest {
    @Getter
    @Setter
    public static class GoogleAuthUrl {
        @NotBlank
        private String redirectUri;
    }

    @Getter
    public static class LoginGoogle {
        @NotBlank
        private String code;
        @NotBlank
        private String state;
        @NotBlank
        private String redirectUri;
        
        private String clientId;
    }

    @Getter
    public static class RefreshToken {
        @NotBlank
        private String refreshToken;
    }
}
