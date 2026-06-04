package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.ShareRewardStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.grpc.ShareRewardWalletClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareCardRepository shareCardRepository;

    @Mock
    private ShareLogRepository shareLogRepository;

    @Mock
    private CharacterOutboxEventRepository characterOutboxEventRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ShareRewardWalletClient shareRewardWalletClient;

    @Mock
    private ShareRewardDispatcher shareRewardDispatcher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShareService shareService;

    @Test
    @DisplayName("createShareCard requires frontend-uploaded imageUrl")
    void createShareCard_withoutImageUrl_throwsException() {
        UserCharacter character = createCharacter(10L, 1L);
        when(userCharacterRepository.findById(10L)).thenReturn(Optional.of(character));

        CharacterException exception = assertThrows(CharacterException.class,
                () -> shareService.createShareCard(1L, 10L, "오늘도 반짝였어요.", ""));

        assertEquals(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL, exception.getErrorCode());
        verify(shareCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("createShareCard stores frontend-uploaded S3 object key")
    void createShareCard_withUploadedImageUrl_success() {
        ReflectionTestUtils.setField(shareService, "publicBaseUrl", "https://p5laris.life");
        UserCharacter character = createCharacter(10L, 1L);
        String imageUrl = "https://d24c6my56k1w5v.cloudfront.net/share-cards/1/card.png";
        String objectKey = "share-cards/1/card.png";

        when(userCharacterRepository.findById(10L)).thenReturn(Optional.of(character));
        when(s3StorageService.publicHost()).thenReturn("d24c6my56k1w5v.cloudfront.net");
        when(s3StorageService.toObjectKey(imageUrl)).thenReturn(objectKey);
        when(s3StorageService.toPublicUrl(objectKey)).thenReturn(imageUrl);
        when(shareCardRepository.findByUserIdAndImageUrl(1L, objectKey)).thenReturn(Optional.empty());
        when(shareCardRepository.save(any(ShareCard.class))).thenAnswer(invocation -> {
            ShareCard card = invocation.getArgument(0);
            ReflectionTestUtils.setField(card, "id", 800L);
            return card;
        });

        var result = shareService.createShareCard(1L, 10L, "오늘도 반짝였어요.", imageUrl);

        assertEquals(800L, result.shareCardId());
        assertNotNull(result.shareId());
        assertEquals(imageUrl, result.imageUrl());
        assertTrue(result.shareUrl().startsWith("https://p5laris.life/share/sh_"));

        ArgumentCaptor<ShareCard> cardCaptor = ArgumentCaptor.forClass(ShareCard.class);
        verify(shareCardRepository).save(cardCaptor.capture());
        assertEquals(objectKey, cardCaptor.getValue().getImageUrl());
    }

    @Test
    @DisplayName("createShareEvent credits wallet for today's first share reward")
    void createShareEvent_firstReward_creditsWallet() throws Exception {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);
        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(anyLong(), any(LocalDate.class)))
                .thenReturn(false);
        when(shareLogRepository.save(any(ShareLog.class))).thenAnswer(invocation -> {
            ShareLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 900L);
            return log;
        });
        when(objectMapper.valueToTree(any())).thenReturn(JsonNodeFactory.instance.objectNode()
                .put("userId", 1L)
                .put("rewardStarPiece", 10));
        when(characterOutboxEventRepository.saveAndFlush(any(CharacterOutboxEvent.class))).thenAnswer(invocation -> {
            CharacterOutboxEvent outbox = invocation.getArgument(0);
            ReflectionTestUtils.setField(outbox, "id", 950L);
            return outbox;
        });
        when(shareRewardDispatcher.dispatchNow(950L))
                .thenReturn(new ShareRewardWalletClient.WalletRewardResult(110, 700L));

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(900L, result.shareEventId());
        assertTrue(result.rewardPaid());
        assertEquals(10, result.rewardStarPiece());
        assertEquals(110, result.walletStarPiece());
        assertEquals(ShareRewardStatus.PAID, result.rewardStatus());
        verify(characterOutboxEventRepository).saveAndFlush(any(CharacterOutboxEvent.class));
        verify(shareRewardDispatcher).dispatchNow(950L);
        verify(shareRewardWalletClient, never()).getWalletStarPiece(anyLong());
    }

    @Test
    @DisplayName("createShareEvent replays existing daily reward without crediting wallet again")
    void createShareEvent_existingReward_replaysWithoutCredit() {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);
        ShareLog existingLog = ShareLog.builder()
                .userId(1L)
                .characterId(10L)
                .shareCardId(800L)
                .shareType("LINK")
                .platform("KAKAO")
                .sharedAt(Instant.now())
                .shareDate(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                .rewardStarPiece(10)
                .rewardPaid(true)
                .idempotencyKey("SHARE_REWARD:1:" + LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                .build();
        ReflectionTestUtils.setField(existingLog, "id", 901L);

        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existingLog));
        when(shareRewardWalletClient.getWalletStarPiece(1L)).thenReturn(110);

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(901L, result.shareEventId());
        assertTrue(result.rewardPaid());
        assertEquals(10, result.rewardStarPiece());
        assertEquals(110, result.walletStarPiece());
        assertEquals(ShareRewardStatus.PAID, result.rewardStatus());
        verify(shareRewardWalletClient, never()).earnShareReward(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("createShareEvent returns PENDING when first share reward dispatch fails")
    void createShareEvent_firstRewardDispatchFails_returnsPending() {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);
        CharacterOutboxEvent pendingOutbox = createPendingOutbox(950L, 900L);

        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(anyLong(), any(LocalDate.class)))
                .thenReturn(false);
        when(shareLogRepository.save(any(ShareLog.class))).thenAnswer(invocation -> {
            ShareLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 900L);
            return log;
        });
        when(objectMapper.valueToTree(any())).thenReturn(JsonNodeFactory.instance.objectNode()
                .put("userId", 1L)
                .put("rewardStarPiece", 10));
        when(characterOutboxEventRepository.saveAndFlush(any(CharacterOutboxEvent.class))).thenAnswer(invocation -> {
            CharacterOutboxEvent outbox = invocation.getArgument(0);
            ReflectionTestUtils.setField(outbox, "id", 950L);
            return outbox;
        });
        when(shareRewardDispatcher.dispatchNow(950L))
                .thenThrow(new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED));
        when(characterOutboxEventRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(pendingOutbox));

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(900L, result.shareEventId());
        assertEquals(10, result.rewardStarPiece());
        assertEquals(0, result.walletStarPiece());
        assertEquals(ShareRewardStatus.PENDING, result.rewardStatus());
    }

    @Test
    @DisplayName("createShareEvent records share but returns NOT_ELIGIBLE after today's reward is paid")
    void createShareEvent_afterDailyRewardPaid_returnsNotEligible() {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);

        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(anyLong(), any(LocalDate.class)))
                .thenReturn(true);
        when(shareLogRepository.save(any(ShareLog.class))).thenAnswer(invocation -> {
            ShareLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 902L);
            return log;
        });

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(902L, result.shareEventId());
        assertEquals(0, result.rewardStarPiece());
        assertEquals(0, result.walletStarPiece());
        assertEquals(ShareRewardStatus.NOT_ELIGIBLE, result.rewardStatus());
        verify(characterOutboxEventRepository, never()).saveAndFlush(any(CharacterOutboxEvent.class));
        verify(shareRewardDispatcher, never()).dispatchNow(anyLong());
    }

    @Test
    @DisplayName("getTodayShareEventStatus returns FAILED when share reward outbox exhausted retries")
    void getTodayShareEventStatus_failedOutbox_returnsFailed() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        ShareLog rewardLog = ShareLog.builder()
                .userId(1L)
                .characterId(10L)
                .shareCardId(800L)
                .shareType("LINK")
                .platform("KAKAO")
                .sharedAt(Instant.now())
                .shareDate(today)
                .rewardStarPiece(10)
                .rewardPaid(false)
                .idempotencyKey("SHARE_REWARD:1:" + today)
                .build();
        CharacterOutboxEvent failedOutbox = createPendingOutbox(950L, 900L);
        failedOutbox.recordFailure("wallet failed", LocalDateTime.now(), 1);

        when(shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(1L, today)).thenReturn(false);
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(rewardLog));
        when(characterOutboxEventRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(failedOutbox));
        when(shareLogRepository.findTopByUserIdAndShareDateOrderBySharedAtDesc(1L, today))
                .thenReturn(Optional.of(rewardLog));

        var result = shareService.getTodayShareEventStatus(1L);

        assertEquals(false, result.rewardClaimed());
        assertEquals(ShareRewardStatus.FAILED.name(), result.rewardStatus());
        assertEquals(rewardLog.getSharedAt().toString(), result.lastSharedAt());
    }

    private void runTransactionTemplateCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private UserCharacter createCharacter(Long characterId, Long userId) {
        CharacterType characterType = CharacterType.builder()
                .code("NOVA")
                .name("Nova")
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
                .name("Nova")
                .level(1)
                .exp(0)
                .fullness(50)
                .energy(50)
                .affection(50)
                .active(true)
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }

    private ShareCard createShareCard(Long shareCardId, Long userId, Long characterId) {
        ShareCard card = ShareCard.builder()
                .userId(userId)
                .characterId(characterId)
                .imageUrl("share-cards/" + userId + "/card.png")
                .headline("오늘도 반짝였어요.")
                .shareUrl("sh_abc12345")
                .build();
        ReflectionTestUtils.setField(card, "id", shareCardId);
        return card;
    }

    private CharacterOutboxEvent createPendingOutbox(Long outboxId, Long shareLogId) {
        CharacterOutboxEvent outbox = CharacterOutboxEvent.pending(
                ShareRewardDispatcher.AGGREGATE_TYPE_SHARE_LOG,
                shareLogId,
                ShareRewardDispatcher.EVENT_TYPE_SHARE_REWARD_REQUESTED,
                JsonNodeFactory.instance.objectNode().put("userId", 1L).put("rewardStarPiece", 10),
                "SHARE_REWARD:1:" + LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", outboxId);
        return outbox;
    }
}
