package p5laris.gateway.domain.share.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.domain.share.api.dto.ShareDto;
import p5laris.gateway.domain.share.infrastructure.grpc.ShareGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

/**
 * Controller for Share APIs (spec §9).
 * Client requests arrive here and are forwarded via gRPC to the Character service.
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareGatewayService shareGatewayService;

    /**
     * 9.1 Create Share Card
     * POST /api/share/v1/share-cards
     */
    @GetMapping("/v1/presigned-url")
    public ApiResponse<ShareDto.PresignedUrlResponse> getSharePresignedUrl(
            @LoginUserId Long userId,
            @RequestParam(defaultValue = "png") String extension) {
        return ApiResponse.success(shareGatewayService.getSharePresignedUrl(userId, extension));
    }

    @PostMapping("/v1/share-cards")
    public ApiResponse<ShareDto.ShareCardResponse> createShareCard(
            @LoginUserId Long userId,
            @RequestBody ShareDto.CreateShareCardRequest request) {
        return ApiResponse.success(shareGatewayService.createShareCard(userId, request));
    }

    /**
     * 9.2 Get Share Card Detail
     * GET /api/share/v1/share-cards/{shareCardId}
     */
    @GetMapping("/v1/share-cards/{shareCardId}")
    public ApiResponse<ShareDto.ShareCardDetailResponse> getShareCard(
            @LoginUserId Long userId,
            @PathVariable Long shareCardId) {
        return ApiResponse.success(shareGatewayService.getShareCard(shareCardId, userId));
    }

    /**
     * 9.3 Create Share Event (and get reward)
     * POST /api/share/v1/share-events
     */
    @PostMapping("/v1/share-events")
    public ApiResponse<ShareDto.ShareEventResponse> createShareEvent(
            @LoginUserId Long userId,
            @RequestBody ShareDto.CreateShareEventRequest request) {
        return ApiResponse.success(shareGatewayService.createShareEvent(userId, request));
    }

    /**
     * 9.4 Get Share Link (Public)
     * GET /api/share/v1/share-links/{shareId}
     */
    @GetMapping("/v1/share-links/{shareId}")
    public ApiResponse<ShareDto.ShareLinkResponse> getShareLink(
            @PathVariable String shareId) {
        return ApiResponse.success(shareGatewayService.getShareLink(shareId));
    }

    /**
     * 9.5 Record Share Click (Public)
     * POST /api/share/v1/share-clicks
     */
    @PostMapping("/v1/share-clicks")
    public ApiResponse<ShareDto.ShareClickResponse> recordShareClick(
            @RequestBody ShareDto.RecordShareClickRequest request) {
        return ApiResponse.success(shareGatewayService.recordShareClick(request));
    }

    @GetMapping("/v1/share-events/today")
    public ApiResponse<ShareDto.TodayShareEventStatusResponse> getTodayShareEventStatus(
            @LoginUserId Long userId) {
        return ApiResponse.success(shareGatewayService.getTodayShareEventStatus(userId));
    }
}
