package p5laris.mission.domain.application.memory;

import com.fasterxml.jackson.databind.JsonNode;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;

import java.time.LocalDateTime;

/**
 * vector 검색으로 찾은 사용자 기억과 거리값이다.
 */
public record UserMemoryRagHit(
        Long userMemoryId,
        UserMemoryType memoryType,
        UserMemorySourceType sourceType,
        String content,
        JsonNode metadataJson,
        int importance,
        LocalDateTime createdAt,
        double distance
) {
}
