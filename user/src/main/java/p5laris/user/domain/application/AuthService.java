package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.core.auth.JwtProvider;
import p5laris.user.core.auth.TokenBlacklistService;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.WalletRepository;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${oauth.google.client-id}")
    private String clientId;

    @Value("${oauth.google.client-secret}")
    private String clientSecret;

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    public String getGoogleAuthUrl(String redirectUri, String state) {
        return GOOGLE_AUTH_URL + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=email%20profile"
                + "&state=" + state;
    }

    @Transactional
    public LoginResult loginGoogle(String code, String redirectUri, String clientIdParam) {
        try {
            // 1. 구글에서 Access Token 가져오기
            String targetClientId = (clientIdParam != null && !clientIdParam.isBlank()) ? clientIdParam : this.clientId;
            boolean isWebClient = targetClientId.equals(this.clientId);

            StringBuilder tokenRequestBodyBuilder = new StringBuilder();
            tokenRequestBodyBuilder.append("code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8))
                    .append("&client_id=").append(targetClientId)
                    .append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
                    .append("&grant_type=authorization_code");

            if (isWebClient) {
                tokenRequestBodyBuilder.append("&client_secret=").append(clientSecret);
            }

            String tokenRequestBody = tokenRequestBodyBuilder.toString();

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) {
                log.error("Google token response: {}", tokenResponse.body());
                throw new RuntimeException("Failed to get Google access token");
            }

            JsonNode tokenNode = objectMapper.readTree(tokenResponse.body());
            String googleAccessToken = tokenNode.get("access_token").asText();

            // 2. 구글에서 유저 정보 가져오기
            HttpRequest userinfoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_USERINFO_URL))
                    .header("Authorization", "Bearer " + googleAccessToken)
                    .GET()
                    .build();

            HttpResponse<String> userinfoResponse = httpClient.send(userinfoRequest, HttpResponse.BodyHandlers.ofString());
            if (userinfoResponse.statusCode() != 200) {
                throw new RuntimeException("Failed to get Google user info");
            }

            JsonNode userNode = objectMapper.readTree(userinfoResponse.body());
            String email = userNode.get("email").asText();
            String name = userNode.has("name") ? userNode.get("name").asText() : "별따라걷기";

            // 3. 유저 조회 혹은 생성
            Optional<User> existingUser = userRepository.findByEmail(email);
            boolean signedUp = existingUser.isEmpty();

            User user = existingUser.orElseGet(() -> {
                User newUser = User.builder()
                        .email(email)
                        .nickname(name)
                        .provider("GOOGLE")
                        .role("USER")
                        .status("ACTIVE")
                        .build();
                return userRepository.save(newUser);
            });

            // 4. 해당 유저의 지갑이 없다면 생성
            if (walletRepository.findByUserId(user.getId()).isEmpty()) {
                walletRepository.save(Wallet.builder().userId(user.getId()).build());
            }

            // 5. 토큰 생성
            String accessToken = jwtProvider.generateAccessToken(user.getId());
            String refreshToken = jwtProvider.generateRefreshToken(user.getId());

            user.updateRefreshToken(refreshToken);

            // 신규 가입 이벤트
            if (signedUp) {
                eventPublisher.publishEvent(UserEventLogEvent.userSignedUp(
                        user.getId(),
                        user.getProvider(),
                        user.getRole(),
                        user.getStatus()
                ));
            }
            // 기존 사용자 로그인 이벤트
            eventPublisher.publishEvent(UserEventLogEvent.userLoggedIn(user.getId(), user.getProvider()));

            return new LoginResult(accessToken, refreshToken, user);

        } catch (Exception e) {
            log.error("Google login failed", e);
            throw new RuntimeException("Google login failed", e);
        }
    }

    @Transactional
    public RefreshResult refreshToken(String refreshToken) {
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        String newAccessToken = jwtProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());

        user.updateRefreshToken(newRefreshToken);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        userRepository.findById(userId).ifPresent(User::clearRefreshToken);

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                java.util.Date expiration = jwtProvider.getExpiration(accessToken);
                tokenBlacklistService.blacklistToken(accessToken, expiration);
            } catch (Exception e) {
                log.warn("Invalid token during logout: {}", e.getMessage());
            }
        }
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {}
    public record RefreshResult(String accessToken, String refreshToken) {}
}
