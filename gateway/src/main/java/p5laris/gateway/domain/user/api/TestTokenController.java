package p5laris.gateway.domain.user.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.global.common.ApiResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/v1/test")
public class TestTokenController {

    private final SecretKey secretKey;

    public TestTokenController(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/token")
    public ApiResponse<Map<String, String>> getTestToken(@RequestParam(defaultValue = "1") Long userId) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(3600000 * 24))) // 24시간 유효
                .signWith(secretKey)
                .compact();

        return ApiResponse.success(Map.of("accessToken", token));
    }
}
