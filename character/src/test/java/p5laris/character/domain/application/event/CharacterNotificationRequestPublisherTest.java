package p5laris.character.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterNotificationRequestPublisherTest {

    @Mock
    private CharacterOutboxEventRepository characterOutboxEventRepository;

    private CharacterNotificationRequestPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CharacterNotificationRequestPublisher(new ObjectMapper(), characterOutboxEventRepository);
    }

    @Test
    void 캐릭터_상태_알림은_character_target을_payload에_담는다() {
        when(characterOutboxEventRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        publisher.requestCharacterStateNotification(1001L, 2001L, "포라", CharacterMood.HUNGRY);

        ArgumentCaptor<CharacterOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(CharacterOutboxEvent.class);
        verify(characterOutboxEventRepository).saveAndFlush(outboxCaptor.capture());

        CharacterOutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getAggregateId()).isEqualTo(2001L);
        assertThat(outbox.getPayload().get("notificationType").asText()).isEqualTo("NOTIFICATION_TYPE_CARE");
        assertThat(outbox.getPayload().get("targetType").asText()).isEqualTo("CHARACTER");
        assertThat(outbox.getPayload().get("targetId").asLong()).isEqualTo(2001L);
    }

    @Test
    void 공유_보상_알림은_share_target과_결정적_멱등키를_payload에_담는다() {
        when(characterOutboxEventRepository.findByIdempotencyKey("SHARE_REWARD_COMPLETED_NOTIFICATION:3001"))
                .thenReturn(Optional.empty());

        publisher.requestShareRewardCompletedNotification(1001L, 3001L, 10);

        ArgumentCaptor<CharacterOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(CharacterOutboxEvent.class);
        verify(characterOutboxEventRepository).saveAndFlush(outboxCaptor.capture());

        CharacterOutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getAggregateId()).isEqualTo(3001L);
        assertThat(outbox.getIdempotencyKey()).isEqualTo("SHARE_REWARD_COMPLETED_NOTIFICATION:3001");
        assertThat(outbox.getPayload().get("notificationType").asText()).isEqualTo("NOTIFICATION_TYPE_SHARE");
        assertThat(outbox.getPayload().get("targetType").asText()).isEqualTo("SHARE");
        assertThat(outbox.getPayload().get("targetId").asLong()).isEqualTo(3001L);
    }

    @Test
    void target이_없는_기존_알림_payload도_역직렬화할_수_있다() throws Exception {
        String legacyPayload = """
                {
                  "userId": 1001,
                  "title": "기존 알림",
                  "body": "기존 payload",
                  "notificationType": "NOTIFICATION_TYPE_CARE"
                }
                """;

        NotificationRequestEvent event = new ObjectMapper()
                .readValue(legacyPayload, NotificationRequestEvent.class);

        assertThat(event.userId()).isEqualTo(1001L);
        assertThat(event.notificationType()).isEqualTo("NOTIFICATION_TYPE_CARE");
        assertThat(event.targetType()).isNull();
        assertThat(event.targetId()).isNull();
    }
}
