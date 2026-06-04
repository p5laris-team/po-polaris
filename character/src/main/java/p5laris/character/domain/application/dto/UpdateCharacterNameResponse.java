package p5laris.character.domain.application.dto;

import lombok.Builder;
import java.time.Instant;

/**
 * 캐릭터 이름 수정 응답 DTO다.
 */
@Builder
public record UpdateCharacterNameResponse(
        Long id,
        String name,
        Instant updatedAt
) {}
