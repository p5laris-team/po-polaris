package p5laris.character.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
public record ShareEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> properties,
        OffsetDateTime occurredAt
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ShareEventLogEvent shareCardCreated(ShareCard card) {
        return new ShareEventLogEvent(
                "SHARE_CARD_CREATED",
                card.getUserId(),
                "SHARE_CARD",
                card.getId(),
                Map.of(
                        "headline", card.getHeadline() != null ? card.getHeadline() : "",
                        "characterId", card.getCharacterId() != null ? card.getCharacterId() : 0L
                ),
                OffsetDateTime.now()
        );
    }

    public static ShareEventLogEvent shareCompleted(ShareLog logRecord) {
        return new ShareEventLogEvent(
                "SHARE_COMPLETED",
                logRecord.getUserId(),
                "SHARE_LOG",
                logRecord.getId(),
                Map.of(
                        "platform", logRecord.getPlatform(),
                        "rewardEarned", logRecord.isRewardPaid()
                ),
                OffsetDateTime.now()
        );
    }

    public String getPropertiesJson() {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.error("공유 이벤트 속성을 JSON으로 직렬화하지 못했습니다.", e);
            return "{}";
        }
    }
}
