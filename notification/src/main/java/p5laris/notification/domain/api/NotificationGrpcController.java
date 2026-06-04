package p5laris.notification.domain.api;

import com.p5laris.proto.notification.v1.GetNotificationsRequest;
import com.p5laris.proto.notification.v1.GetNotificationsResponse;
import com.p5laris.proto.notification.v1.GetNotificationSettingRequest;
import com.p5laris.proto.notification.v1.GetNotificationSettingResponse;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountRequest;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountResponse;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadRequest;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadResponse;
import com.p5laris.proto.notification.v1.MarkNotificationReadRequest;
import com.p5laris.proto.notification.v1.MarkNotificationReadResponse;
import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.RegisterFcmTokenRequest;
import com.p5laris.proto.notification.v1.RegisterFcmTokenResponse;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingRequest;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.notification.core.exception.ErrorCode;
import p5laris.notification.domain.application.NotificationService;
import p5laris.notification.domain.exception.NotificationErrorCode;
import p5laris.notification.domain.exception.NotificationException;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class NotificationGrpcController extends NotificationServiceGrpc.NotificationServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "알림 서비스 처리 중 오류가 발생했습니다.";

    private final NotificationService notificationService;
    private final p5laris.notification.domain.application.FcmSenderService fcmSenderService;

    // 로그인한 사용자의 앱 내부 알림 목록을 조회한다.
    @Override
    public void getNotifications(
            GetNotificationsRequest request,
            StreamObserver<GetNotificationsResponse> responseObserver
    ) {
        try {
            GetNotificationsResponse response = notificationService.getNotifications(
                    request.getUserId(),
                    request.hasRead() ? request.getRead() : null,
                    request.getCursor(),
                    request.getSize()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("알림 목록 조회", e));
        }
    }

    // 알림 읽음 여부를 변경한다.
    @Override
    public void markNotificationRead(
            MarkNotificationReadRequest request,
            StreamObserver<MarkNotificationReadResponse> responseObserver
    ) {
        try {
            MarkNotificationReadResponse response = notificationService.markNotificationRead(
                    request.getUserId(),
                    request.getNotificationId(),
                    request.getRead()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("알림 읽음 처리", e));
        }
    }

    // 로그인한 사용자의 안 읽은 알림을 모두 읽음 처리한다.
    @Override
    public void markAllNotificationsRead(
            MarkAllNotificationsReadRequest request,
            StreamObserver<MarkAllNotificationsReadResponse> responseObserver
    ) {
        try {
            MarkAllNotificationsReadResponse response =
                    notificationService.markAllNotificationsRead(request.getUserId());

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("알림 모두 읽음 처리", e));
        }
    }

    // FCM registration token을 등록하거나 갱신한다.
    @Override
    public void registerFcmToken(
            RegisterFcmTokenRequest request,
            StreamObserver<RegisterFcmTokenResponse> responseObserver
    ) {
        try {
            RegisterFcmTokenResponse response = notificationService.registerFcmToken(
                    request.getUserId(),
                    request.getToken()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("FCM 토큰 등록", e));
        }
    }

    // 로그인한 사용자의 알림 수신 설정을 조회한다.
    @Override
    public void getNotificationSetting(
            GetNotificationSettingRequest request,
            StreamObserver<GetNotificationSettingResponse> responseObserver
    ) {
        try {
            GetNotificationSettingResponse response = notificationService.getNotificationSetting(request.getUserId());

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("알림 설정 조회", e));
        }
    }

    // 로그인한 사용자의 알림 수신 설정을 갱신한다.
    @Override
    public void updateNotificationSetting(
            UpdateNotificationSettingRequest request,
            StreamObserver<UpdateNotificationSettingResponse> responseObserver
    ) {
        try {
            UpdateNotificationSettingResponse response = notificationService.updateNotificationSetting(
                    request.getUserId(),
                    request
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("알림 설정 갱신", e));
        }
    }

    // 다른 모듈의 요청을 받아 FCM 푸시 알림을 비동기로 발송한다.
    @Override
    public void sendPushNotification(
            com.p5laris.proto.notification.v1.SendPushNotificationRequest request,
            StreamObserver<com.p5laris.proto.notification.v1.SendPushNotificationResponse> responseObserver
    ) {
        try {
            // DB에 알림 이력 먼저 생성 및 저장 (동기)
            p5laris.notification.domain.domain.entity.Notification notification = notificationService.createNotification(request);

            // 비동기로 FCM 발송 이력 기록 및 실제 푸시 발송
            fcmSenderService.sendPushNotification(
                    notification.getId(),
                    request.getUserId(),
                    request.getTitle(),
                    request.getBody(),
                    notification.getNotificationType()
            );

            responseObserver.onNext(com.p5laris.proto.notification.v1.SendPushNotificationResponse.newBuilder()
                    .setSuccess(true)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("푸시 알림 요청", e));
        }
    }

    // 안 읽은 알림 개수를 조회한다.
    @Override
    public void getUnreadNotificationCount(
            GetUnreadNotificationCountRequest request,
            StreamObserver<GetUnreadNotificationCountResponse> responseObserver
    ) {
        try {
            GetUnreadNotificationCountResponse response = notificationService.getUnreadNotificationCount(request.getUserId());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("안 읽은 알림 개수 조회", e));
        }
    }

    private String safeDescription(NotificationException e) {
        return e.getErrorCode().getCode();
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("알림 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }

    private Status toStatus(NotificationException e) {
        ErrorCode errorCode = e.getErrorCode();

        if (errorCode == NotificationErrorCode.NOTIFICATION_NOT_FOUND) {
            return Status.NOT_FOUND;
        }

        if (errorCode == NotificationErrorCode.INVALID_NOTIFICATION_REQUEST
                || errorCode == NotificationErrorCode.INVALID_FCM_TOKEN) {
            return Status.INVALID_ARGUMENT;
        }

        return Status.INTERNAL;
    }
}
