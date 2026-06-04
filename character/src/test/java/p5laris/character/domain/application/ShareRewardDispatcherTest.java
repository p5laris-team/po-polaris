package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;
import p5laris.character.domain.infrastructure.grpc.ShareRewardWalletClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ShareRewardDispatcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 0, 0);

    @Mock
    private CharacterOutboxEventRepository characterOutboxEventRepository;

    @Mock
    private ShareLogRepository shareLogRepository;

    @Mock
    private ShareRewardWalletClient shareRewardWalletClient;

    @Mock
    private NotificationPushClient notificationPushClient;

    @Mock
    private ShareRewardBackoffPolicy shareRewardBackoffPolicy;

    @Mock
    private ShareRewardOutboxProperties properties;

    @Mock
    private TransactionTemplate transactionTemplate;

    private SimpleMeterRegistry meterRegistry;
    private ShareRewardDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new ShareRewardDispatcher(
                characterOutboxEventRepository,
                shareLogRepository,
                shareRewardWalletClient,
                notificationPushClient,
                shareRewardBackoffPolicy,
                properties,
                transactionTemplate,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-01T15:00:00Z"), ZoneId.of("Asia/Seoul")),
                meterRegistry
        );
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        dispatcher.init();
    }

    @Test
    void gauge_pending_count_is_registered_correctly() {
        when(characterOutboxEventRepository.countByStatus(CharacterOutboxEventStatus.PENDING)).thenReturn(8L);

        double count = meterRegistry.find("outbox.pending.count").gauge().value();
        assertThat(count).isEqualTo(8.0);
    }

    @Test
    void dispatchDue_requestsNotificationAfterRetrySuccess() {
        CharacterOutboxEvent outbox = pendingOutbox(950L, 900L);
        ShareLog shareLog = shareLog(900L, false);

        whenDispatchable(outbox);
        when(shareLogRepository.findByIdForUpdate(900L)).thenReturn(Optional.of(shareLog));
        when(shareRewardWalletClient.earnShareReward(1L, 900L, 10, "SHARE_REWARD:1:2026-06-02"))
                .thenReturn(new ShareRewardWalletClient.WalletRewardResult(110, 700L));

        int succeededCount = dispatcher.dispatchDue(100);

        assertThat(succeededCount).isEqualTo(1);
        assertThat(shareLog.isRewardPaid()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(CharacterOutboxEventStatus.SUCCEEDED);
        verify(notificationPushClient).sendShareRewardCompletedNotification(1L, 900L, 10);

        // Counter 검증
        var counter = meterRegistry.find("outbox.events.processed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTag("status")).isEqualTo("SUCCESS");
        assertThat(counter.getId().getTag("aggregate_type")).isEqualTo("SHARE_LOG");
    }

    @Test
    void dispatchDue_keepsRewardSuccessWhenNotificationFails() {
        CharacterOutboxEvent outbox = pendingOutbox(950L, 900L);
        ShareLog shareLog = shareLog(900L, false);

        whenDispatchable(outbox);
        when(shareLogRepository.findByIdForUpdate(900L)).thenReturn(Optional.of(shareLog));
        when(shareRewardWalletClient.earnShareReward(1L, 900L, 10, "SHARE_REWARD:1:2026-06-02"))
                .thenReturn(new ShareRewardWalletClient.WalletRewardResult(110, 700L));
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationPushClient)
                .sendShareRewardCompletedNotification(1L, 900L, 10);

        int succeededCount = dispatcher.dispatchDue(100);

        assertThat(succeededCount).isEqualTo(1);
        assertThat(shareLog.isRewardPaid()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(CharacterOutboxEventStatus.SUCCEEDED);
    }

    private void whenDispatchable(CharacterOutboxEvent outbox) {
        when(characterOutboxEventRepository.findDispatchableIds(
                eq(ShareRewardDispatcher.EVENT_TYPE_SHARE_REWARD_REQUESTED),
                eq(CharacterOutboxEventStatus.PENDING),
                eq(CharacterOutboxEventStatus.PROCESSING),
                eq(NOW),
                any(Pageable.class)
        )).thenReturn(List.of(outbox.getId()));
        when(characterOutboxEventRepository.findByIdForUpdate(outbox.getId()))
                .thenReturn(Optional.of(outbox), Optional.of(outbox));
        when(properties.getProcessingTimeoutSeconds()).thenReturn(120L);
    }

    private CharacterOutboxEvent pendingOutbox(Long outboxId, Long shareLogId) {
        CharacterOutboxEvent outbox = CharacterOutboxEvent.pending(
                ShareRewardDispatcher.AGGREGATE_TYPE_SHARE_LOG,
                shareLogId,
                ShareRewardDispatcher.EVENT_TYPE_SHARE_REWARD_REQUESTED,
                JsonNodeFactory.instance.objectNode()
                        .put("userId", 1L)
                        .put("rewardStarPiece", 10),
                "SHARE_REWARD:1:2026-06-02",
                NOW.minusMinutes(1)
        );
        ReflectionTestUtils.setField(outbox, "id", outboxId);
        return outbox;
    }

    private ShareLog shareLog(Long shareLogId, boolean rewardPaid) {
        ShareLog shareLog = ShareLog.builder()
                .userId(1L)
                .characterId(10L)
                .shareCardId(800L)
                .shareType("LINK")
                .platform("KAKAO")
                .sharedAt(Instant.now())
                .shareDate(LocalDate.of(2026, 6, 2))
                .rewardStarPiece(10)
                .rewardPaid(rewardPaid)
                .idempotencyKey("SHARE_REWARD:1:2026-06-02")
                .build();
        ReflectionTestUtils.setField(shareLog, "id", shareLogId);
        return shareLog;
    }
}
