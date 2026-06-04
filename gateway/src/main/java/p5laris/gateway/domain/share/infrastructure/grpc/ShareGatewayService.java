package p5laris.gateway.domain.share.infrastructure.grpc;

import com.p5laris.proto.character.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.share.api.dto.ShareDto;
import lombok.RequiredArgsConstructor;

/**
 * gRPC client service for Share APIs.
 * Connects to the character module where Share logic lives.
 */
@Service
@RequiredArgsConstructor
public class ShareGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    public ShareDto.PresignedUrlResponse getSharePresignedUrl(Long userId, String extension) {
        var response = characterStub.getSharePresignedUrl(
                com.p5laris.proto.character.v1.GetSharePresignedUrlRequest.newBuilder()
                        .setUserId(userId)
                        .setExtension(extension)
                        .build()
        );
        return new ShareDto.PresignedUrlResponse(
                response.getPresignedUrl(),
                response.getImageUrl()
        );
    }

    // §9.1
    public ShareDto.ShareCardResponse createShareCard(Long userId, ShareDto.CreateShareCardRequest request) {
        var response = characterStub.createShareCard(
                CreateShareCardRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterId(request.characterId())
                        .setHeadline(request.headline() != null ? request.headline() : "")
                        .setImageUrl(request.imageUrl() != null ? request.imageUrl() : "")
                        .build()
        );
        return new ShareDto.ShareCardResponse(
                response.getShareCardId(),
                response.getShareId(),
                response.getImageUrl(),
                response.getShareUrl()
        );
    }

    // §9.2
    public ShareDto.ShareCardDetailResponse getShareCard(Long shareCardId, Long userId) {
        var response = characterStub.getShareCard(
                GetShareCardRequest.newBuilder()
                        .setShareCardId(shareCardId)
                        .setUserId(userId)
                        .build()
        );
        return new ShareDto.ShareCardDetailResponse(
                response.getShareCardId(),
                response.getCharacterName(),
                response.getImageUrl(),
                response.getShareUrl()
        );
    }

    // §9.3
    public ShareDto.ShareEventResponse createShareEvent(Long userId, ShareDto.CreateShareEventRequest request) {
        var response = characterStub.createShareEvent(
                CreateShareEventRequest.newBuilder()
                        .setUserId(userId)
                        .setShareCardId(request.shareCardId())
                        .setPlatform(request.platform())
                        .setShareType(request.shareType())
                        .setIdempotencyKey(request.idempotencyKey())
                        .build()
        );
        return new ShareDto.ShareEventResponse(
                response.getShareEventId(),
                response.getRewardPaid(),
                response.getRewardStarPiece(),
                response.getRewardStatus(),
                new ShareDto.ShareEventResponse.WalletInfo(response.getWalletStarPiece())
        );
    }

    // §9.4
    public ShareDto.ShareLinkResponse getShareLink(String shareId) {
        var response = characterStub.getShareLink(
                GetShareLinkRequest.newBuilder()
                        .setShareId(shareId)
                        .build()
        );
        return new ShareDto.ShareLinkResponse(
                response.getShareId(),
                response.getCharacterName(),
                response.getImageUrl(),
                response.getHeadline(),
                response.getSignupUrl()
        );
    }

    // §9.5
    public ShareDto.ShareClickResponse recordShareClick(ShareDto.RecordShareClickRequest request) {
        var response = characterStub.recordShareClick(
                RecordShareClickRequest.newBuilder()
                        .setShareId(request.shareId() != null ? request.shareId() : "")
                        .setReferrer(request.referrer() != null ? request.referrer() : "")
                        .setUtmSource(request.utmSource() != null ? request.utmSource() : "")
                        .setUtmMedium(request.utmMedium() != null ? request.utmMedium() : "")
                        .setUtmCampaign(request.utmCampaign() != null ? request.utmCampaign() : "")
                        .build()
        );
        return new ShareDto.ShareClickResponse(
                response.getShareId(),
                response.getRecorded()
        );
    }

    public ShareDto.TodayShareEventStatusResponse getTodayShareEventStatus(Long userId) {
        var response = characterStub.getTodayShareEventStatus(
                GetTodayShareEventStatusRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );
        return new ShareDto.TodayShareEventStatusResponse(
                response.getRewardClaimed(),
                response.getLastSharedAt(),
                response.getRewardStatus()
        );
    }
}
