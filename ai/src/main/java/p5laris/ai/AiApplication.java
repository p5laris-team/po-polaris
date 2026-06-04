package p5laris.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.config.AiRateLimitProperties;

/**
 * AI 도메인 서비스 애플리케이션이다.
 *
 * 외부 REST 요청을 직접 받는 모듈이 아니라, mission/gateway 등 내부 서비스가 gRPC로 호출하는 서버 역할을 한다.
 */
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({
        AiProviderProperties.class,
        AiRateLimitProperties.class,
        AiEmbeddingProperties.class,
        AiCircuitBreakerProperties.class,
        AiCharacterTalkProperties.class
})
@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }

}
