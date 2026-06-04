package p5laris.gateway.domain.share.api.dto;

public class ShareDto {

    // ---------- Presigned URL ----------
    public record PresignedUrlResponse(
            String presignedUrl,
            String imageUrl
    ) {}

    // §9.1 CreateShareCard
    public record CreateShareCardRequest(
            Long characterId,
            String headline,
            String imageUrl
    ) {}

    public record ShareCardResponse(
            Long shareCardId,
            String shareId,
            String imageUrl,
            String shareUrl
    ) {}

    // §9.2 GetShareCard
    public record ShareCardDetailResponse(
            Long shareCardId,
            String characterName,
            String imageUrl,
            String shareUrl
    ) {}

    // §9.3 CreateShareEvent
    public record CreateShareEventRequest(
            Long shareCardId,
            String platform,
            String shareType,
            String idempotencyKey
    ) {}

    public record ShareEventResponse(
            Long shareEventId,
            boolean rewardPaid,
            int rewardStarPiece,
            String rewardStatus,
            WalletInfo wallet
    ) {
        public record WalletInfo(int starPiece) {}
    }

    // §9.4 GetShareLink (Public)
    public record ShareLinkResponse(
            String shareId,
            String characterName,
            String imageUrl,
            String headline,
            String signupUrl
    ) {}

    // §9.5 RecordShareClick (Public)
    public record RecordShareClickRequest(
            String shareId,
            String referrer,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {}

    public record ShareClickResponse(
            String shareId,
            boolean recorded
    ) {}

    public record TodayShareEventStatusResponse(
            boolean rewardClaimed,
            String lastSharedAt,
            String rewardStatus
    ) {}
}
