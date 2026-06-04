package p5laris.ai.domain.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 모듈에서 JSON snapshot을 만들기 위한 ObjectMapper 설정이다.
 *
 * ai 모듈은 Web starter를 쓰지 않으므로 ObjectMapper bean을 직접 등록한다.
 */
@Configuration
public class AiJsonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
