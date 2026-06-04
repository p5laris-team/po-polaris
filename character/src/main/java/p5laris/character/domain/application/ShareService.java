package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;
import p5laris.character.domain.domain.enums.ShareRewardStatus;
import p5laris.character.domain.domain.repository.CharacterOutboxEventRepository;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.grpc.ShareRewardWalletClient;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 공유 카드와 공유 이벤트 API를 처리하는 서비스다.
 *
 * ShareCard / ShareLog 엔티티는 캐릭터 모듈에서 관리한다.
 * 공유 보상 별조각은 지갑 모듈 gRPC와 character outbox 재처리 흐름으로 지급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final int SHARE_REWARD_STAR_PIECE = 10;
    private static final String PLACEHOLDER_HEADLINE = "Today, I shone a little.";
    private static final int HEADLINE_MAX_LENGTH = 100;
    private static final ZoneId SHARE_DATE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SHARE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShareCardRepository shareCardRepository;
    private final ShareLogRepository shareLogRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final CharacterOutboxEventRepository characterOutboxEventRepository;
    private final S3StorageService s3StorageService;
    private final UserCharacterRepository userCharacterRepository;
    private final ShareRewardWalletClient shareRewardWalletClient;
    private final ShareRewardDispatcher shareRewardDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    // ---------- 공유 카드 생성 ----------

    @Transactional
    public ShareCardResult createShareCard(Long userId, Long characterId, String headline, String imageUrl) {
        String safeHeadline = normalizeAndValidateHeadline(headline);

        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        String resolvedImageKey = validateShareCardImageUrlAndExtractKey(userId, imageUrl);

        return shareCardRepository.findByUserIdAndImageUrl(userId, resolvedImageKey)
                .map(card -> {
                    String shareId = extractShareId(card.getShareUrl());
                    return new ShareCardResult(
                        card.getId(),
                        shareId,
                        s3StorageService.toPublicUrl(card.getImageUrl()),
                        buildShareUrl(shareId));
                })
                .orElseGet(() -> {
                    String shareId = "sh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    
                    ShareCard card = ShareCard.builder()
                            .userId(userId)
                            .characterId(characterId)
                            .imageUrl(resolvedImageKey)
                            .headline(safeHeadline)
                            .shareUrl(shareId)
                            .build();
                    shareCardRepository.save(card);
                    eventPublisher.publishEvent(p5laris.character.domain.application.event.ShareEventLogEvent.shareCardCreated(card));

                    return new ShareCardResult(
                            card.getId(),
                            shareId,
                            s3StorageService.toPublicUrl(resolvedImageKey),
                            buildShareUrl(shareId));
                });
    }

    // ---------- 공유 카드 상세 조회 ----------

    /**
     * 소유자 기준으로 공유 카드 상세 정보를 조회한다.
     * API 명세 9.2 GET /api/share/v1/share-cards/{shareCardId}
     */
    @Transactional(readOnly = true)
    public ShareCardDetailResult getShareCard(Long shareCardId, Long userId) {
        ShareCard card = shareCardRepository.findById(shareCardId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_SHARE_CARD_OWNER);
        }

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareCardDetailResult(
                card.getId(),
                character.getName(),
                s3StorageService.toPublicUrl(card.getImageUrl()),
                buildShareUrl(extractShareId(card.getShareUrl())));
    }

    // ---------- 공유 이벤트 기록 ----------

    /**
     * 공유 이벤트를 기록하고 하루 1회 공유 보상을 멱등 키 기준으로 처리한다.
     * API 명세 9.3 POST /api/share/v1/share-events
     *
     * 지갑 지급은 같은 트랜잭션에 묶지 않고 outbox 발행 뒤 즉시 시도하거나 재처리한다.
     * 보상 지급 실패는 공유 실패로 보지 않고 rewardStatus=PENDING/FAILED로 분리한다.
     */
    public ShareEventResult createShareEvent(Long userId, Long shareCardId,
                                             String platform, String shareType,
                                             String idempotencyKey) {
        ShareRewardCommand command = transactionTemplate.execute(status ->
                recordShareEvent(userId, shareCardId, platform, shareType, idempotencyKey)
        );

        if (command.rewardOutboxId() != null) {
            try {
                int walletStarPiece = shareRewardDispatcher.dispatchNow(command.rewardOutboxId()).starPiece();
                return new ShareEventResult(
                        command.shareLogId(),
                        true,
                        command.rewardStarPiece(),
                        walletStarPiece,
                        ShareRewardStatus.PAID
                );
            } catch (CharacterException e) {
                ShareRewardStatus rewardStatus = resolveRewardStatus(
                        command.rewardIdempotencyKey(),
                        ShareRewardStatus.PENDING
                );
                return new ShareEventResult(
                        command.shareLogId(),
                        false,
                        command.rewardStarPiece(),
                        0,
                        rewardStatus
                );
            }
        }

        int walletStarPiece = command.rewardStatus() == ShareRewardStatus.PAID
                ? getWalletStarPieceOrZero(userId)
                : 0;
        return new ShareEventResult(
                command.shareLogId(),
                command.rewardStatus() == ShareRewardStatus.PAID,
                command.rewardStarPiece(),
                walletStarPiece,
                command.rewardStatus()
        );
    }

    private ShareRewardCommand recordShareEvent(Long userId, Long shareCardId,
                                                String platform, String shareType,
                                                String idempotencyKey) {
        ShareCard card = shareCardRepository.findById(shareCardId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_SHARE_CARD_OWNER);
        }

        // 하루 1회 공유 보상 지급 가능 여부를 확인한다. (AGENTS.md §20.5)
        LocalDate today = LocalDate.now(SHARE_DATE_ZONE);
        String rewardIdempotencyKey = buildDailyShareRewardIdempotencyKey(userId, today);

        // 이미 기록된 일일 보상 로그가 있으면 기존 지급 상태를 그대로 재사용한다.
        var existingRewardLog = shareLogRepository.findByIdempotencyKey(rewardIdempotencyKey);
        if (existingRewardLog.isPresent()) {
            ShareLog log = existingRewardLog.get();
            ShareRewardStatus rewardStatus = log.isRewardPaid()
                    ? ShareRewardStatus.PAID
                    : resolveRewardStatus(rewardIdempotencyKey, ShareRewardStatus.FAILED);
            return new ShareRewardCommand(
                    log.getId(),
                    null,
                    log.getRewardStarPiece(),
                    rewardStatus,
                    rewardIdempotencyKey
            );
        }

        boolean alreadyRewarded = shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(userId, today);
        boolean rewardEligible = !alreadyRewarded;
        int rewardAmount = rewardEligible ? SHARE_REWARD_STAR_PIECE : 0;

        String finalIdempotencyKey = rewardEligible
                ? rewardIdempotencyKey
                : (idempotencyKey != null && !idempotencyKey.isBlank()
                    ? idempotencyKey
                    : "SHARE_EVENT:" + userId + ":" + UUID.randomUUID());

        ShareLog shareLog = ShareLog.builder()
                .userId(userId)
                .characterId(card.getCharacterId())
                .shareCardId(shareCardId)
                .shareType(shareType)
                .platform(platform)
                .sharedAt(Instant.now())
                .shareDate(today)
                .rewardStarPiece(rewardAmount)
                .rewardPaid(false)
                .idempotencyKey(finalIdempotencyKey)
                .build();
        shareLogRepository.save(shareLog);

        if (!rewardEligible) {
            return new ShareRewardCommand(
                    shareLog.getId(),
                    null,
                    rewardAmount,
                    ShareRewardStatus.NOT_ELIGIBLE,
                    rewardIdempotencyKey
            );
        }

        CharacterOutboxEvent outbox = CharacterOutboxEvent.pending(
                ShareRewardDispatcher.AGGREGATE_TYPE_SHARE_LOG,
                shareLog.getId(),
                ShareRewardDispatcher.EVENT_TYPE_SHARE_REWARD_REQUESTED,
                objectMapper.valueToTree(new ShareRewardDispatcher.ShareRewardPayload(userId, rewardAmount)),
                rewardIdempotencyKey,
                LocalDateTime.now(SHARE_DATE_ZONE)
        );
        characterOutboxEventRepository.saveAndFlush(outbox);

        eventPublisher.publishEvent(p5laris.character.domain.application.event.ShareEventLogEvent.shareCompleted(shareLog));

        return new ShareRewardCommand(
                shareLog.getId(),
                outbox.getId(),
                rewardAmount,
                ShareRewardStatus.PENDING,
                rewardIdempotencyKey
        );
    }

    // ---------- §9.4 공유 링크 공개 조회 ----------

    /**
     * 인증 없이 shareId로 공개 공유 카드 정보를 조회한다.
     * API 명세 9.4 GET /api/share/v1/share-links/{shareId}
     */
    @Transactional(readOnly = true)
    public ShareLinkResult getShareLink(String shareId) {
        ShareCard card = shareCardRepository.findByShareUrl(shareId)
                .or(() -> shareCardRepository.findByShareUrl(buildShareUrl(shareId)))
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_LINK_NOT_FOUND));

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareLinkResult(
                shareId,
                character.getName(),
                s3StorageService.toPublicUrl(card.getImageUrl()),
                card.getHeadline() != null ? card.getHeadline() : PLACEHOLDER_HEADLINE,
                buildSignupUrl(shareId));
    }

    // ---------- §9.5 공유 링크 클릭 기록 ----------

    /**
     * 인증 없이 공유 링크 클릭을 기록한다.
     * API 명세 9.5 POST /api/share/v1/share-clicks
     *
     * MVP에서는 운영 로그만 남기고 recorded=true를 반환한다.
     * 이후 분석 저장소가 확정되면 클릭 로그 영속화를 별도 정책으로 붙인다.
     */
    public ShareClickResult recordShareClick(String shareId, String referrer,
                                             String utmSource, String utmMedium,
                                             String utmCampaign) {
        log.info("공유 링크 클릭을 기록했습니다. shareId={}, referrer={}, utm={}/{}/{}",
                shareId, referrer, utmSource, utmMedium, utmCampaign);
        return new ShareClickResult(shareId, true);
    }

    // ---------- Presigned URL 발급 ----------

    public S3StorageService.S3PresignedResult getSharePresignedUrl(Long userId, String extension) {
        return s3StorageService.generatePresignedUrlForShareCard(userId, extension);
    }

    // ---------- 내부 헬퍼 ----------

    private String normalizeAndValidateHeadline(String headline) {
        String normalized = headline == null ? PLACEHOLDER_HEADLINE : headline.trim();
        if (normalized.isBlank()) {
            normalized = PLACEHOLDER_HEADLINE;
        }
        if (normalized.length() > HEADLINE_MAX_LENGTH) {
            throw new CharacterException(CharacterErrorCode.INVALID_SHARE_HEADLINE);
        }
        return normalized;
    }

    private String validateShareCardImageUrlAndExtractKey(Long userId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new CharacterException(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
        }
        try {
            URI uri = URI.create(imageUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String expectedUserPrefix = "/share-cards/" + userId + "/";
            if (!"https".equalsIgnoreCase(scheme)
                    || s3StorageService.publicHost() == null
                    || !s3StorageService.publicHost().equalsIgnoreCase(host)
                    || path == null
                    || !path.startsWith(expectedUserPrefix)
                    || !hasAllowedImageExtension(path)) {
                throw new CharacterException(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
            }
            return s3StorageService.toObjectKey(uri.toString());
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
        }
    }

    private boolean hasAllowedImageExtension(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".png")
                || lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg")
                || lowerPath.endsWith(".webp");
    }

    private String extractShareId(String shareUrl) {
        int lastSlash = shareUrl.lastIndexOf('/');
        return lastSlash >= 0 ? shareUrl.substring(lastSlash + 1) : shareUrl;
    }

    private String buildShareUrl(String shareId) {
        return normalizedPublicBaseUrl() + "/share/" + shareId;
    }

    private String buildSignupUrl(String shareId) {
        return normalizedPublicBaseUrl() + "/signup?shareId=" + shareId;
    }

    private String normalizedPublicBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("app.public-base-url 설정이 필요합니다.");
        }

        String normalized = publicBaseUrl.trim();
        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String buildDailyShareRewardIdempotencyKey(Long userId, LocalDate shareDate) {
        return "SHARE_REWARD:" + userId + ":" + SHARE_DATE_FORMATTER.format(shareDate);
    }

    @Transactional(readOnly = true)
    public TodayShareEventStatusResult getTodayShareEventStatus(Long userId) {
        LocalDate today = LocalDate.now(SHARE_DATE_ZONE);
        ShareRewardStatus rewardStatus = resolveTodayRewardStatus(userId, today);
        boolean rewardClaimed = rewardStatus == ShareRewardStatus.PAID;
        String lastSharedAt = shareLogRepository.findTopByUserIdAndShareDateOrderBySharedAtDesc(userId, today)
                .map(ShareLog::getSharedAt)
                .map(Instant::toString)
                .orElse("");
        return new TodayShareEventStatusResult(rewardClaimed, lastSharedAt, rewardStatus.name());
    }

    private ShareRewardStatus resolveTodayRewardStatus(Long userId, LocalDate today) {
        if (shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(userId, today)) {
            return ShareRewardStatus.PAID;
        }

        String rewardIdempotencyKey = buildDailyShareRewardIdempotencyKey(userId, today);
        return shareLogRepository.findByIdempotencyKey(rewardIdempotencyKey)
                .map(log -> resolveRewardStatus(rewardIdempotencyKey, ShareRewardStatus.FAILED))
                .orElse(ShareRewardStatus.AVAILABLE);
    }

    private ShareRewardStatus resolveRewardStatus(String rewardIdempotencyKey, ShareRewardStatus fallback) {
        if (rewardIdempotencyKey == null || rewardIdempotencyKey.isBlank()) {
            return fallback;
        }

        return characterOutboxEventRepository.findByIdempotencyKey(rewardIdempotencyKey)
                .map(this::toShareRewardStatus)
                .orElse(fallback);
    }

    private ShareRewardStatus toShareRewardStatus(CharacterOutboxEvent outbox) {
        if (outbox.getStatus() == CharacterOutboxEventStatus.SUCCEEDED) {
            return ShareRewardStatus.PAID;
        }
        if (outbox.getStatus() == CharacterOutboxEventStatus.FAILED) {
            return ShareRewardStatus.FAILED;
        }
        return ShareRewardStatus.PENDING;
    }

    private int getWalletStarPieceOrZero(Long userId) {
        try {
            return shareRewardWalletClient.getWalletStarPiece(userId);
        } catch (CharacterException e) {
            return 0;
        }
    }

    // ---------- 결과 record ----------

    public record ShareCardResult(Long shareCardId, String shareId, String imageUrl, String shareUrl) {}
    public record ShareCardDetailResult(Long shareCardId, String characterName, String imageUrl, String shareUrl) {}
    public record ShareEventResult(
            Long shareEventId,
            boolean rewardPaid,
            int rewardStarPiece,
            int walletStarPiece,
            ShareRewardStatus rewardStatus
    ) {}
    public record ShareLinkResult(String shareId, String characterName, String imageUrl, String headline, String signupUrl) {}
    public record ShareClickResult(String shareId, boolean recorded) {}
    public record TodayShareEventStatusResult(boolean rewardClaimed, String lastSharedAt, String rewardStatus) {}
    private record ShareRewardCommand(
            Long shareLogId,
            Long rewardOutboxId,
            int rewardStarPiece,
            ShareRewardStatus rewardStatus,
            String rewardIdempotencyKey
    ) {}
}
