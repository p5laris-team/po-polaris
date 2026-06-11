package p5laris.character.domain.application.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.enums.CharacterExpSourceType;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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

    public static CharacterEventLogEvent careActionPerformed(
            UserCharacter character,
            Long careLogId,
            Long itemId,
            ActionType actionType,
            int expGained,
            boolean levelUp
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("careLogId", careLogId);
        properties.put("itemId", itemId);
        properties.put("actionType", actionType.name());
        properties.put("expGained", expGained);
        properties.put("levelUp", levelUp);
        properties.put("level", character.getLevel());
        properties.put("exp", character.getExp());
        properties.put("fullness", character.getFullness());
        properties.put("energy", character.getEnergy());
        properties.put("affection", character.getAffection());

        return new CharacterEventLogEvent(
                "CARE_ACTION_PERFORMED",
                character.getUserId(),
                "CHARACTER_CARE_LOG",
                careLogId,
                properties,
                OffsetDateTime.now()
        );
    }

    public static CharacterEventLogEvent expGranted(
            UserCharacter character,
            Long expLogId,
            CharacterExpSourceType sourceType,
            Long sourceId,
            int expAmount,
            int beforeExp,
            int afterExp,
            int beforeLevel,
            int afterLevel,
            boolean levelUp
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expLogId", expLogId);
        properties.put("sourceType", sourceType.name());
        properties.put("sourceId", sourceId);
        properties.put("expAmount", expAmount);
        properties.put("beforeExp", beforeExp);
        properties.put("afterExp", afterExp);
        properties.put("beforeLevel", beforeLevel);
        properties.put("afterLevel", afterLevel);
        properties.put("levelUp", levelUp);

        return new CharacterEventLogEvent(
                "CHARACTER_EXP_GRANTED",
                character.getUserId(),
                "CHARACTER_EXP_LOG",
                expLogId,
                properties,
                OffsetDateTime.now()
        );
    }

    public static CharacterEventLogEvent skinChanged(UserCharacter character, Long beforeSkinId, Long afterSkinId) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("beforeSkinId", beforeSkinId);
        properties.put("afterSkinId", afterSkinId);
        properties.put("equipped", afterSkinId != null);

        return new CharacterEventLogEvent(
                afterSkinId == null ? "CHARACTER_SKIN_UNEQUIPPED" : "CHARACTER_SKIN_EQUIPPED",
                character.getUserId(),
                "CHARACTER",
                character.getId(),
                properties,
                OffsetDateTime.now()
        );
    }

    public static CharacterEventLogEvent storyUnlocked(
            UserCharacter character,
            Long storyFragmentId,
            String memoryKey,
            int unlockedLevel,
            String triggerType
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("storyFragmentId", storyFragmentId);
        properties.put("memoryKey", memoryKey);
        properties.put("unlockedLevel", unlockedLevel);
        properties.put("triggerType", triggerType);

        return new CharacterEventLogEvent(
                "CHARACTER_STORY_UNLOCKED",
                character.getUserId(),
                "CHARACTER_STORY_FRAGMENT",
                storyFragmentId,
                properties,
                OffsetDateTime.now()
        );
    }

    @JsonIgnore
    public String getPropertiesJson() {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.error("캐릭터 이벤트 속성을 JSON으로 직렬화하지 못했습니다.", e);
            return "{}";
        }
    }
}
