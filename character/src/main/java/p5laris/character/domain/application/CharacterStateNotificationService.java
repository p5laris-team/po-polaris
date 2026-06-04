package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterStateNotificationService {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 1_000;

    private final UserCharacterRepository userCharacterRepository;
    private final NotificationPushClient notificationPushClient;
    private final TransactionTemplate transactionTemplate;

    public int dispatchDueStateNotifications(int batchSize) {
        int normalizedBatchSize = normalizeBatchSize(batchSize);
        int pageNumber = 0;
        int requestedCount = 0;

        Page<Long> page;
        do {
            page = userCharacterRepository.findActiveIds(
                    PageRequest.of(pageNumber, normalizedBatchSize, Sort.by("id").ascending())
            );

            for (Long characterId : page.getContent()) {
                Optional<NotificationCommand> command = transactionTemplate.execute(status ->
                        buildNotificationCommandIfNeeded(characterId)
                );
                if (command.isPresent() && requestNotification(command.get())) {
                    requestedCount++;
                }
            }

            pageNumber++;
        } while (page.hasNext());

        return requestedCount;
    }

    private Optional<NotificationCommand> buildNotificationCommandIfNeeded(Long characterId) {
        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElse(null);
        if (character == null || !character.isActive()) {
            return Optional.empty();
        }

        boolean decreased = character.calculateTimeBasedStatDecrease();
        if (!decreased) {
            return Optional.empty();
        }

        CharacterMood mood = character.calculateMood();
        if (mood == CharacterMood.IDLE || mood == CharacterMood.HAPPY || mood == CharacterMood.SLEEPY) {
            return Optional.empty();
        }

        if (character.getUserId() == null || character.getId() == null) {
            log.warn("userId 또는 characterId가 없어 캐릭터 상태 알림 요청을 건너뜁니다. userId={}, characterId={}",
                    character.getUserId(), character.getId());
            return Optional.empty();
        }

        return Optional.of(new NotificationCommand(
                character.getUserId(),
                character.getId(),
                character.getName(),
                mood
        ));
    }

    private boolean requestNotification(NotificationCommand command) {
        try {
            notificationPushClient.sendCharacterStateNotification(
                    command.userId(),
                    command.characterId(),
                    command.characterName(),
                    command.mood()
            );
            return true;
        } catch (Exception e) {
            log.warn("캐릭터 상태 알림 요청에 실패했습니다. userId={}, characterId={}, mood={}",
                    command.userId(), command.characterId(), command.mood(), e);
            return false;
        }
    }

    private int normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }

        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private record NotificationCommand(Long userId, Long characterId, String characterName, CharacterMood mood) {
    }
}
