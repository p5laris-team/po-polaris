package p5laris.character.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CharacterNotificationRequestPublisher {

    public static final String AGGREGATE_TYPE_NOTIFICATION_REQUEST = "NOTIFICATION_REQUEST";
    public static final String EVENT_TYPE_NOTIFICATION_REQUESTED = "NOTIFICATION_REQUESTED";

    private static final String DEFAULT_CHARACTER_NAME = "별친구";
    private static final String SHARE_REWARD_TITLE = "공유 보상 지급 완료";

    private final ObjectMapper objectMapper;
    private final CharacterOutboxEventRepository characterOutboxEventRepository;

    public void requestCharacterStateNotification(
            Long userId,
            Long characterId,
            String characterName,
            CharacterMood mood
    ) {
        NotificationMessage message = toCharacterStateMessage(displayName(characterName), mood);
        save(
                characterId,
                new NotificationRequestEvent(
                        userId,
                        message.title(),
                        message.body(),
                        "NOTIFICATION_TYPE_CARE"
                ),
                UUID.randomUUID().toString()
        );
    }

    public void requestShareRewardCompletedNotification(
            Long userId,
            Long shareLogId,
            int rewardStarPiece
    ) {
        save(
                shareLogId,
                new NotificationRequestEvent(
                        userId,
                        SHARE_REWARD_TITLE,
                        "별조각 " + rewardStarPiece + "개가 도착했어요.",
                        "NOTIFICATION_TYPE_SHARE"
                ),
                "SHARE_REWARD_COMPLETED_NOTIFICATION:" + shareLogId
        );
    }

    private void save(Long aggregateId, NotificationRequestEvent event, String idempotencyKey) {
        if (characterOutboxEventRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }

        CharacterOutboxEvent outboxEvent = CharacterOutboxEvent.pending(
                AGGREGATE_TYPE_NOTIFICATION_REQUEST,
                aggregateId,
                EVENT_TYPE_NOTIFICATION_REQUESTED,
                objectMapper.valueToTree(event),
                idempotencyKey,
                LocalDateTime.now()
        );
        characterOutboxEventRepository.saveAndFlush(outboxEvent);
    }

    private NotificationMessage toCharacterStateMessage(String characterName, CharacterMood mood) {
        return switch (mood) {
            case HUNGRY -> new NotificationMessage(
                    characterName + "가 배고파 보여요",
                    "별사탕밥을 챙겨 줄까요?"
            );
            case LOW_ENERGY -> new NotificationMessage(
                    characterName + "가 졸려 보여요",
                    "구름 베개로 쉬게 해 줄까요?"
            );
            case LONELY -> new NotificationMessage(
                    characterName + "가 조금 심심해 보여요",
                    "별빛 장난감으로 놀아 줄까요?"
            );
            default -> new NotificationMessage(
                    characterName + "의 상태를 확인해 주세요",
                    "별친구가 돌봄을 기다리고 있어요."
            );
        };
    }

    private String displayName(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return DEFAULT_CHARACTER_NAME;
        }
        return characterName.trim();
    }

    private record NotificationMessage(String title, String body) {
    }
}
