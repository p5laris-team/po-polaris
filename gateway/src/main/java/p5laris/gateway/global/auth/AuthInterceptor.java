package p5laris.gateway.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtValidator jwtValidator;
    private final StringRedisTemplate redisTemplate;
    private final boolean failClosed;

    public AuthInterceptor(
            JwtValidator jwtValidator,
            StringRedisTemplate redisTemplate,
            @Value("${gateway.auth.blacklist.fail-closed:false}") boolean failClosed
    ) {
        this.jwtValidator = jwtValidator;
        this.redisTemplate = redisTemplate;
        this.failClosed = failClosed;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
        }

        boolean isBlacklisted = false;
        try {
            isBlacklisted = Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
        } catch (Exception e) {
            log.error("Failed to check blacklist in Redis for token. failClosed: {}", failClosed, e);
            if (failClosed) {
                throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
            }
        }

        if (isBlacklisted) {
            throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
        }

        try {
            Long userId = jwtValidator.validateAndGetUserId(token);
            request.setAttribute("userId", userId);
            return true;
        } catch (Exception e) {
            log.error("Invalid token", e);
            throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
