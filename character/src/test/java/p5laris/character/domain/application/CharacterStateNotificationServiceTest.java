package p5laris.character.domain.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterStateNotificationServiceTest {

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private NotificationPushClient notificationPushClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private CharacterStateNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CharacterStateNotificationService(userCharacterRepository, notificationPushClient, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void dispatchDueStateNotifications_requestsNotificationWhenMoodNeedsCare() {
        UserCharacter character = character(10L, 1L, "Mumu", 45, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        mockActiveCharacter(character);

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isEqualTo(1);
        verify(notificationPushClient).sendCharacterStateNotification(
                1L,
                10L,
                "Mumu",
                CharacterMood.HUNGRY
        );
    }

    @Test
    void dispatchDueStateNotifications_skipsNotificationWhenMoodIsNormal() {
        UserCharacter character = character(10L, 1L, "Mumu", 90, 90, 90);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        mockActiveCharacter(character);

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isZero();
        verify(notificationPushClient, never()).sendCharacterStateNotification(any(), any(), any(), any());
    }

    @Test
    void dispatchDueStateNotifications_skipsNotificationWhenDecreaseCycleIsNotDue() {
        UserCharacter character = character(10L, 1L, "Mumu", 30, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(2 * 3600));
        mockActiveCharacter(character);

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isZero();
        verify(notificationPushClient, never()).sendCharacterStateNotification(any(), any(), any(), any());
    }

    @Test
    void dispatchDueStateNotifications_doesNotStopWhenNotificationFails() {
        UserCharacter character = character(10L, 1L, "Mumu", 45, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        mockActiveCharacter(character);
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationPushClient)
                .sendCharacterStateNotification(1L, 10L, "Mumu", CharacterMood.HUNGRY);

        assertThatCode(() -> service.dispatchDueStateNotifications(100))
                .doesNotThrowAnyException();
    }

    private void mockActiveCharacter(UserCharacter character) {
        when(userCharacterRepository.findActiveIds(any()))
                .thenReturn(new PageImpl<>(List.of(character.getId())));
        when(userCharacterRepository.findById(character.getId()))
                .thenReturn(Optional.of(character));
    }

    private UserCharacter character(
            Long characterId,
            Long userId,
            String name,
            int fullness,
            int energy,
            int affection
    ) {
        CharacterType characterType = CharacterType.builder()
                .code("MUMU")
                .name("Mumu")
                .summary("summary")
                .personality("personality")
                .speechStyle("speech")
                .introMessage("intro")
                .sampleLine("sample")
                .active(true)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(characterType, "id", 1L);

        UserCharacter character = UserCharacter.builder()
                .userId(userId)
                .characterType(characterType)
                .name(name)
                .level(1)
                .exp(0)
                .fullness(fullness)
                .energy(energy)
                .affection(affection)
                .active(true)
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }
}
