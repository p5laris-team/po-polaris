package p5laris.ai.domain.application.memory;

import java.time.LocalDateTime;

/**
 * 별친구 대화 장기 기억 검색 결과다.
 */
public record CharacterTalkMemoryHit(
        Long id,
        String summary,
        double distance,
        LocalDateTime createdAt
) {
}
