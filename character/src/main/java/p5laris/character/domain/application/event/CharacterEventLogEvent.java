package p5laris.character.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import p5laris.character.domain.domain.entity.UserCharacter;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
public record CharacterEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> properties,
        OffsetDateTime occurredAt
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static CharacterEventLogEvent characterCreated(UserCharacter character) {
        return new CharacterEventLogEvent(
                "CHARACTER_CREATED",
                character.getUserId(),
                "CHARACTER",
                character.getId(),
                Map.of(
                        "characterTypeCode", character.getCharacterType().getCode(),
                        "name", character.getName()
                ),
                OffsetDateTime.now()
        );
    }

    public String getPropertiesJson() {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.error("캐릭터 이벤트 속성을 JSON으로 직렬화하지 못했습니다.", e);
            return "{}";
        }
    }
}
