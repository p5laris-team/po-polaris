package p5laris.gateway.global.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private StringRedisTemplate redisTemplate;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("정상 토큰이며 블랙리스트에 없으면 preHandle 통과")
    void preHandle_success() {
        // given
        String token = "valid-token";
        request.addHeader("Authorization", "Bearer " + token);
        
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(false);
        when(jwtValidator.validateAndGetUserId(token)).thenReturn(100L);

        AuthInterceptor interceptor = new AuthInterceptor(jwtValidator, redisTemplate, false);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        assertThat(request.getAttribute("userId")).isEqualTo(100L);
        verify(redisTemplate, times(1)).hasKey("blacklist:" + token);
        verify(jwtValidator, times(1)).validateAndGetUserId(token);
    }

    @Test
    @DisplayName("토큰이 비어있으면 preHandle 실패 (INVALID_TOKEN)")
    void preHandle_emptyToken_throwsException() {
        // given
        request.addHeader("Authorization", "");

        AuthInterceptor interceptor = new AuthInterceptor(jwtValidator, redisTemplate, false);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("블랙리스트에 등록된 토큰이면 preHandle 실패 (INVALID_TOKEN)")
    void preHandle_blacklistedToken_throwsException() {
        // given
        String token = "blacklisted-token";
        request.addHeader("Authorization", "Bearer " + token);

        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(true);

        AuthInterceptor interceptor = new AuthInterceptor(jwtValidator, redisTemplate, false);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_TOKEN);

        verify(jwtValidator, never()).validateAndGetUserId(any());
    }

    @Test
    @DisplayName("Redis 에러 발생 시 fail-closed가 false(기본값)이면 Fail-Open으로 검증 통과")
    void preHandle_redisError_failOpen_success() {
        // given
        String token = "valid-token";
        request.addHeader("Authorization", "Bearer " + token);

        when(redisTemplate.hasKey("blacklist:" + token)).thenThrow(new RedisConnectionFailureException("Redis connection failed"));
        when(jwtValidator.validateAndGetUserId(token)).thenReturn(100L);

        // fail-closed = false (기본값)
        AuthInterceptor interceptor = new AuthInterceptor(jwtValidator, redisTemplate, false);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        assertThat(request.getAttribute("userId")).isEqualTo(100L);
        verify(redisTemplate, times(1)).hasKey("blacklist:" + token);
        verify(jwtValidator, times(1)).validateAndGetUserId(token);
    }

    @Test
    @DisplayName("Redis 에러 발생 시 fail-closed가 true이면 Fail-Closed로 차단 (INVALID_TOKEN)")
    void preHandle_redisError_failClosed_throwsException() {
        // given
        String token = "valid-token";
        request.addHeader("Authorization", "Bearer " + token);

        when(redisTemplate.hasKey("blacklist:" + token)).thenThrow(new RedisConnectionFailureException("Redis connection failed"));

        // fail-closed = true
        AuthInterceptor interceptor = new AuthInterceptor(jwtValidator, redisTemplate, true);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_TOKEN);

        verify(redisTemplate, times(1)).hasKey("blacklist:" + token);
        verify(jwtValidator, never()).validateAndGetUserId(any());
    }
}
