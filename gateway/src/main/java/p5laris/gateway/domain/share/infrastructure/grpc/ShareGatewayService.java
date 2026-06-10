package p5laris.gateway.domain.share.infrastructure.grpc;

import com.p5laris.proto.character.v1.*;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.share.api.dto.ShareDto;
import lombok.RequiredArgsConstructor;
import p5laris.gateway.domain.share.exception.ShareGatewayErrorCode;
import p5laris.gateway.domain.share.exception.ShareGatewayException;

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
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    // §9.1
    public ShareDto.ShareCardResponse createShareCard(Long userId, ShareDto.CreateShareCardRequest request) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    // §9.2
    public ShareDto.ShareCardDetailResponse getShareCard(Long shareCardId, Long userId) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    // §9.3
    public ShareDto.ShareEventResponse createShareEvent(Long userId, ShareDto.CreateShareEventRequest request) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    // §9.4
    public ShareDto.ShareLinkResponse getShareLink(String shareId) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    // §9.5
    public ShareDto.ShareClickResponse recordShareClick(ShareDto.RecordShareClickRequest request) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    public ShareDto.TodayShareEventStatusResponse getTodayShareEventStatus(Long userId) {
        try {
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
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    private ShareGatewayException toGatewayException(StatusRuntimeException e) {
        io.grpc.Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();

        if (code == io.grpc.Status.Code.UNAVAILABLE) {
            return new ShareGatewayException(ShareGatewayErrorCode.SHARE_SERVICE_UNAVAILABLE);
        }

        if (code == io.grpc.Status.Code.INVALID_ARGUMENT && description != null) {
            if (description.contains("CH017")) {
                return new ShareGatewayException(ShareGatewayErrorCode.INVALID_IDEMPOTENCY_KEY);
            }
            if (description.contains("CH014")) {
                return new ShareGatewayException(ShareGatewayErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
            }
            if (description.contains("CH006")) {
                return new ShareGatewayException(ShareGatewayErrorCode.SHARE_CARD_NOT_FOUND);
            }
            if (description.contains("CH007")) {
                return new ShareGatewayException(ShareGatewayErrorCode.NOT_SHARE_CARD_OWNER);
            }
            if (description.contains("CH008")) {
                return new ShareGatewayException(ShareGatewayErrorCode.SHARE_LINK_NOT_FOUND);
            }
            if (description.contains("CH009")) {
                return new ShareGatewayException(ShareGatewayErrorCode.INVALID_SHARE_HEADLINE);
            }
            if (description.contains("CH016")) {
                return new ShareGatewayException(ShareGatewayErrorCode.SHARE_REWARD_FAILED);
            }
        }

        return new ShareGatewayException(ShareGatewayErrorCode.SHARE_SERVICE_UNAVAILABLE);
    }
}
