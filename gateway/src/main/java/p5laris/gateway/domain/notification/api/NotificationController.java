package p5laris.gateway.domain.notification.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.notification.api.dto.NotificationDto;
import p5laris.gateway.domain.notification.infrastructure.grpc.NotificationGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

/**
 * notification 도메인을 외부 REST API로 노출하는 gateway 컨트롤러다.
 *
 * <p>실제 알림 조회, 읽음 처리, FCM token 저장은 notification gRPC 서버가 담당하고,
 * 이 컨트롤러는 로그인한 userId를 붙여 내부 gRPC 호출로 전달하는 입구 역할만 한다.</p>
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationGatewayService notificationGatewayService;

    /**
     * 로그인한 사용자의 앱 내부 알림 목록을 조회한다.
     */
    @GetMapping("/v1/notifications")
    public ApiResponse<NotificationDto.NotificationsResponse> getNotifications(
            @LoginUserId Long userId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(notificationGatewayService.getNotifications(
                userId,
                read,
                cursor,
                size
        ));
    }

    /**
     * 로그인한 사용자의 안 읽은 알림을 모두 읽음 처리한다.
     */
    @PatchMapping("/v1/notifications/read-all")
    public ApiResponse<NotificationDto.MarkAllNotificationsReadResponse> markAllNotificationsRead(
            @LoginUserId Long userId
    ) {
        return ApiResponse.success(notificationGatewayService.markAllNotificationsRead(userId));
    }

    /**
     * 알림 읽음 여부를 변경한다.
     */
    @PatchMapping("/v1/notifications/{notificationId}")
    public ApiResponse<NotificationDto.UpdateNotificationReadResponse> updateNotificationRead(
            @LoginUserId Long userId,
            @PathVariable Long notificationId,
            @Valid @RequestBody NotificationDto.UpdateNotificationReadRequest request
    ) {
        return ApiResponse.success(notificationGatewayService.updateNotificationRead(
                userId,
                notificationId,
                request
        ));
    }

    /**
     * FCM registration token을 등록하거나 갱신한다.
     */
    @PostMapping("/v1/subscriptions/")
    public ApiResponse<NotificationDto.RegisterFcmTokenResponse> registerFcmToken(
            @LoginUserId Long userId,
            @Valid @RequestBody NotificationDto.RegisterFcmTokenRequest request
    ) {
        return ApiResponse.success(notificationGatewayService.registerFcmToken(userId, request));
    }

    /**
     * 로그인한 사용자의 알림 수신 설정을 조회한다.
     */
    @GetMapping("/v1/settings")
    public ApiResponse<NotificationDto.NotificationSettingResponse> getNotificationSetting(
            @LoginUserId Long userId
    ) {
        return ApiResponse.success(notificationGatewayService.getNotificationSetting(userId));
    }

    /**
     * 로그인한 사용자의 알림 수신 설정을 갱신한다.
     */
    @PatchMapping("/v1/settings")
    public ApiResponse<NotificationDto.NotificationSettingResponse> updateNotificationSetting(
            @LoginUserId Long userId,
            @Valid @RequestBody NotificationDto.UpdateNotificationSettingRequest request
    ) {
        return ApiResponse.success(notificationGatewayService.updateNotificationSetting(userId, request));
    }
}
