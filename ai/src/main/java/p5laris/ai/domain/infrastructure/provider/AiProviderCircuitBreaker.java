package p5laris.ai.domain.infrastructure.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;

import java.util.function.Supplier;

/**
 * 외부 AI provider 호출을 보호하는 서킷 브레이커다.
 *
 * 반복 실패/느린 호출이 감지되면 일정 시간 provider 호출을 생략하고 기존 fallback 흐름으로 넘긴다.
 */
@Component
@Slf4j
public class AiProviderCircuitBreaker {

    private final AiCircuitBreakerProperties properties;
    private final CircuitBreaker circuitBreaker;

    public AiProviderCircuitBreaker(AiCircuitBreakerProperties properties) {
        this.properties = properties;
        this.circuitBreaker = CircuitBreaker.of("ai-provider", circuitBreakerConfig(properties));
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "AI provider 서킷 상태가 변경되었습니다. from={}, to={}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ));
    }

    public <T> T execute(
            AiProviderType providerType,
            String model,
            String requestId,
            Supplier<T> supplier
    ) {
        if (!properties.isEnabled()) {
            return supplier.get();
        }

        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException e) {
            log.warn(
                    "AI provider 서킷이 열려 외부 호출을 건너뜁니다. provider={}, model={}, requestId={}",
                    providerType,
                    model,
                    requestId
            );
            throw new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "AI provider 서킷이 열려 있습니다.");
        }
    }

    private CircuitBreakerConfig circuitBreakerConfig(AiCircuitBreakerProperties properties) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.normalizedSlidingWindowSize())
                .minimumNumberOfCalls(properties.normalizedMinimumNumberOfCalls())
                .failureRateThreshold(properties.normalizedFailureRateThreshold())
                .slowCallDurationThreshold(properties.slowCallDuration())
                .slowCallRateThreshold(properties.normalizedSlowCallRateThreshold())
                .waitDurationInOpenState(properties.waitDurationOpen())
                .build();
    }
}
