package p5laris.gateway.domain.notification.infrastructure.grpc;

import com.p5laris.proto.notification.v1.GetNotificationsRequest;
import com.p5laris.proto.notification.v1.GetNotificationsResponse;
import com.p5laris.proto.notification.v1.GetNotificationSettingRequest;
import com.p5laris.proto.notification.v1.GetNotificationSettingResponse;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadRequest;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadResponse;
import com.p5laris.proto.notification.v1.MarkNotificationReadRequest;
import com.p5laris.proto.notification.v1.MarkNotificationReadResponse;
import com.p5laris.proto.notification.v1.Notification;
import com.p5laris.proto.notification.v1.NotificationSettingSnapshot;
import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.RegisterFcmTokenRequest;
import com.p5laris.proto.notification.v1.RegisterFcmTokenResponse;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountRequest;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountResponse;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingRequest;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.notification.api.dto.NotificationDto;
import p5laris.gateway.domain.notification.exception.NotificationGatewayErrorCode;
import p5laris.gateway.domain.notification.exception.NotificationGatewayException;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

/**
 * notification gRPC 서버를 호출하고 REST DTO로 변환하는 gateway adapter다.
 *
 * <p>gateway는 notification DB를 직접 보지 않는다.
 * REST 요청에서 받은 값으로 protobuf request를 만들고,
 * notification 서버 응답을 다시 REST 응답 DTO로 변환한다.</p>
 */
@Service
public class NotificationGatewayService {

    private static final long EMPTY_CURSOR = 0L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    @GrpcClient("notification")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    /**
     * 로그인한 사용자의 앱 내부 알림 목록을 조회한다.
     */
    public NotificationDto.NotificationsResponse getNotifications(
            Long userId,
            Boolean read,
            Long cursor,
            Integer size
    ) {
        validateUserId(userId);

        try {
            GetNotificationsRequest.Builder requestBuilder = GetNotificationsRequest.newBuilder()
                    .setUserId(userId)
                    .setCursor(toGrpcCursor(cursor))
                    .setSize(normalizeSize(size));

            if (read != null) {
                requestBuilder.setRead(read);
            }

            GetNotificationsResponse response = notificationStub.getNotifications(requestBuilder.build());

            return new NotificationDto.NotificationsResponse(
                    response.getNotificationsList().stream()
                            .map(this::toNotificationItem)
                            .toList(),
                    new NotificationDto.PageInfo(
                            response.getPageInfo().getNextCursor() == EMPTY_CURSOR
                                    ? null
                                    : response.getPageInfo().getNextCursor(),
                            response.getPageInfo().getHasNext(),
                            response.getPageInfo().getSize()
                    )
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 로그인한 사용자의 안 읽은 알림 개수를 조회한다.
     */
    public int getUnreadNotificationCount(Long userId) {
        validateUserId(userId);

        try {
            GetUnreadNotificationCountResponse response = notificationStub.getUnreadNotificationCount(
                    GetUnreadNotificationCountRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            return (int) response.getUnreadCount();
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 알림 읽음 여부를 변경한다.
     */
    public NotificationDto.UpdateNotificationReadResponse updateNotificationRead(
            Long userId,
            Long notificationId,
            NotificationDto.UpdateNotificationReadRequest request
    ) {
        validateUserId(userId);
        validateNotificationId(notificationId);
        validateReadRequest(request);

        try {
            MarkNotificationReadResponse response = notificationStub.markNotificationRead(
                    MarkNotificationReadRequest.newBuilder()
                            .setUserId(userId)
                            .setNotificationId(notificationId)
                            .setRead(request.read())
                            .build()
            );

            return new NotificationDto.UpdateNotificationReadResponse(
                    response.getId(),
                    response.getRead(),
                    emptyToNull(response.getUpdatedAt())
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 로그인한 사용자의 안 읽은 알림을 모두 읽음 처리한다.
     */
    public NotificationDto.MarkAllNotificationsReadResponse markAllNotificationsRead(Long userId) {
        validateUserId(userId);

        try {
            MarkAllNotificationsReadResponse response = notificationStub.markAllNotificationsRead(
                    MarkAllNotificationsReadRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            return new NotificationDto.MarkAllNotificationsReadResponse(
                    response.getUpdatedCount(),
                    response.getUnreadCount(),
                    emptyToNull(response.getUpdatedAt())
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * FCM registration token을 등록하거나 갱신한다.
     */
    public NotificationDto.RegisterFcmTokenResponse registerFcmToken(
            Long userId,
            NotificationDto.RegisterFcmTokenRequest request
    ) {
        validateUserId(userId);
        validateFcmTokenRequest(request);

        try {
            RegisterFcmTokenResponse response = notificationStub.registerFcmToken(
                    RegisterFcmTokenRequest.newBuilder()
                            .setUserId(userId)
                            .setToken(request.token())
                            .build()
            );

            return new NotificationDto.RegisterFcmTokenResponse(
                    response.getId(),
                    emptyToNull(response.getCreatedAt())
            );
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 로그인한 사용자의 알림 수신 설정을 조회한다.
     */
    public NotificationDto.NotificationSettingResponse getNotificationSetting(Long userId) {
        validateUserId(userId);

        try {
            GetNotificationSettingResponse response = notificationStub.getNotificationSetting(
                    GetNotificationSettingRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            return toNotificationSettingResponse(response.getSetting());
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    /**
     * 로그인한 사용자의 알림 수신 설정을 갱신한다.
     */
    public NotificationDto.NotificationSettingResponse updateNotificationSetting(
            Long userId,
            NotificationDto.UpdateNotificationSettingRequest request
    ) {
        validateUserId(userId);
        validateSettingRequest(request);

        try {
            UpdateNotificationSettingResponse response = notificationStub.updateNotificationSetting(
                    UpdateNotificationSettingRequest.newBuilder()
                            .setUserId(userId)
                            .setPushEnabled(request.pushEnabled())
                            .setMissionOfferEnabled(request.missionOfferEnabled())
                            .setCharacterStateEnabled(request.characterStateEnabled())
                            .setDailyReminderEnabled(request.dailyReminderEnabled())
                            .setQuietHoursEnabled(request.quietHoursEnabled())
                            .setQuietHoursStart(request.quietHoursStart())
                            .setQuietHoursEnd(request.quietHoursEnd())
                            .build()
            );

            return toNotificationSettingResponse(response.getSetting());
        } catch (StatusRuntimeException e) {
            throw toGatewayException(e);
        }
    }

    private NotificationDto.NotificationItem toNotificationItem(Notification notification) {
        return new NotificationDto.NotificationItem(
                notification.getId(),
                toRestNotificationType(notification.getNotificationType().name()),
                notification.getTitle(),
                notification.getMessage(),
                notification.hasTargetType()
                        ? toRestTargetType(notification.getTargetType().name())
                        : null,
                notification.hasTargetId()
                        ? notification.getTargetId()
                        : null,
                notification.getRead(),
                emptyToNull(notification.getCreatedAt())
        );
    }

    private NotificationDto.NotificationSettingResponse toNotificationSettingResponse(
            NotificationSettingSnapshot setting
    ) {
        return new NotificationDto.NotificationSettingResponse(
                setting.getPushEnabled(),
                setting.getMissionOfferEnabled(),
                setting.getCharacterStateEnabled(),
                setting.getDailyReminderEnabled(),
                setting.getQuietHoursEnabled(),
                setting.getQuietHoursStart(),
                setting.getQuietHoursEnd()
        );
    }

    private long toGrpcCursor(Long cursor) {
        if (cursor == null) {
            return EMPTY_CURSOR;
        }

        if (cursor < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        return cursor;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateNotificationId(Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateReadRequest(NotificationDto.UpdateNotificationReadRequest request) {
        if (request == null || request.read() == null) {
            throw new NotificationGatewayException(NotificationGatewayErrorCode.INVALID_NOTIFICATION_REQUEST);
        }
    }

    private void validateFcmTokenRequest(NotificationDto.RegisterFcmTokenRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new NotificationGatewayException(NotificationGatewayErrorCode.INVALID_FCM_TOKEN);
        }
    }

    private void validateSettingRequest(NotificationDto.UpdateNotificationSettingRequest request) {
        if (request == null
                || request.pushEnabled() == null
                || request.missionOfferEnabled() == null
                || request.characterStateEnabled() == null
                || request.dailyReminderEnabled() == null
                || request.quietHoursEnabled() == null
                || request.quietHoursStart() == null
                || request.quietHoursEnd() == null) {
            throw new NotificationGatewayException(NotificationGatewayErrorCode.INVALID_NOTIFICATION_REQUEST);
        }
    }

    private String toRestNotificationType(String grpcType) {
        return removeGrpcPrefix(grpcType, "NOTIFICATION_TYPE_");
    }

    private String toRestTargetType(String grpcType) {
        return removeGrpcPrefix(grpcType, "TARGET_TYPE_");
    }

    private String removeGrpcPrefix(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) {
            return value;
        }

        return value.substring(prefix.length());
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    /**
     * notification gRPC status를 gateway의 REST 실패 응답 코드로 바꾼다.
     */
    private BusinessException toGatewayException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();

        return switch (code) {
            case NOT_FOUND -> new NotificationGatewayException(NotificationGatewayErrorCode.NOTIFICATION_NOT_FOUND);
            case INVALID_ARGUMENT -> new NotificationGatewayException(NotificationGatewayErrorCode.INVALID_NOTIFICATION_REQUEST);
            case UNAVAILABLE -> new NotificationGatewayException(NotificationGatewayErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
            default -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        };
    }
}
