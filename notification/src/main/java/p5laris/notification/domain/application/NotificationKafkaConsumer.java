package p5laris.notification.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.notification.v1.NotificationType;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import com.p5laris.proto.notification.v1.TargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import p5laris.notification.domain.application.event.NotificationRequestEvent;
import p5laris.notification.domain.domain.entity.Notification;
import p5laris.notification.domain.exception.KafkaConsumerProcessingException;

/**
 * Notification 모듈의 Kafka 메시지 컨슈머 클래스입니다.
 * 
 * [역할]
 * 1. 'notification-requests' 토픽을 구독하여 타 모듈(예: user 모듈의 출석 체크)에서 요청한 푸시 메시지 알림 이벤트를 비동기로 수신합니다.
 * 2. 수신한 DTO를 기반으로 로컬 Notification 데이터베이스에 알림 수신 내역을 동기식으로 먼저 인서트(저장)합니다.
 * 3. 저장된 내역을 바탕으로 실제 FCM(Firebase Cloud Messaging) 디바이스 푸시 발송 처리를 비동기로 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final FcmSenderService fcmSenderService;
    
    // 타 서비스 모듈 간의 DTO 패키지 충돌(__TypeId__) 우회를 위한 역직렬화 도구
    private final ObjectMapper objectMapper;

    /**
     * 'notification-requests' 토픽으로 인입되는 푸시 발송 메시지를 처리하는 리스너 메서드입니다.
     * 패키지 네임스페이스 격리를 확보하기 위해 String 타입의 raw payload를 파싱합니다.
     *
     * @param messagePayload 푸시 정보 JSON 포맷 문자열
     * @param idempotencyKey 중복 처리 방지용 멱등키 (Kafka Message Key)
     */
    @KafkaListener(topics = "notification-requests", groupId = "notification-group")
    public void consumeNotificationRequest(
            String messagePayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String idempotencyKey
    ) {
        NotificationRequestEvent event;
        try {
            event = objectMapper.readValue(messagePayload, NotificationRequestEvent.class);
        } catch (Exception e) {
            log.error("[Kafka] 알림 발송 요청 메시지 역직렬화(JSON 파싱) 실패 - 멱등키: {}", idempotencyKey, e);
            throw new KafkaConsumerProcessingException("알림 요청 메시지 역직렬화에 실패했습니다.", e);
        }

        log.info("[Kafka] 알림 발송 요청 수신 - 사용자: {}, 제목: {}, 타입: {}, 멱등키: {}", 
                event.userId(), event.title(), event.notificationType(), idempotencyKey);
        
        try {
            // 1. 이벤트 문자열 타입의 notificationType을 proto enum 타입으로 매핑
            NotificationType protoType = mapToProtoType(event.notificationType());
            TargetType targetType = mapToTargetType(event.targetType());

            // 2. 서비스 호출을 위한 proto Request 빌드
            SendPushNotificationRequest.Builder requestBuilder = SendPushNotificationRequest.newBuilder()
                    .setUserId(event.userId())
                    .setTitle(event.title())
                    .setBody(event.body())
                    .setNotificationType(protoType);

            if (targetType != TargetType.TARGET_TYPE_UNSPECIFIED) {
                requestBuilder.setTargetType(targetType);
            }
            if (event.targetId() != null) {
                requestBuilder.setTargetId(event.targetId());
            }

            SendPushNotificationRequest protoRequest = requestBuilder.build();

            // 3. DB에 알림 이력과 FCM 발송 대기 이력을 같은 트랜잭션으로 생성한다.
            Notification notification = notificationService.createNotification(protoRequest, idempotencyKey);

            // 4. 이미 처리된 중복 메시지는 발송할 PENDING delivery가 없으므로 no-op이 된다.
            fcmSenderService.dispatchPendingDeliveries(notification.getId());

            log.info("[Kafka] 알림 푸시 발송 및 DB 기록 위임 성공 - 알림 ID: {}", notification.getId());
        } catch (Exception e) {
            log.error("[Kafka] 알림 발송 처리 중 예외 발생 - 멱등키: {}", idempotencyKey, e);
            throw new KafkaConsumerProcessingException("알림 요청 처리에 실패했습니다.", e);
        }
    }

    /**
     * DTO의 알림 타입 문자열 정보를 protobuf 형식의 NotificationType Enum으로 형변환 매핑해 줍니다.
     */
    private NotificationType mapToProtoType(String typeStr) {
        if (typeStr == null) {
            return NotificationType.NOTIFICATION_TYPE_SYSTEM;
        }
        try {
            return NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // 만약 'ATTENDANCE' 등의 짧은 형태로 들어온 경우 'NOTIFICATION_TYPE_ATTENDANCE' 로 치환 시도
            String formatted = "NOTIFICATION_TYPE_" + typeStr.toUpperCase();
            try {
                return NotificationType.valueOf(formatted);
            } catch (Exception ex) {
                return NotificationType.NOTIFICATION_TYPE_SYSTEM;
            }
        }
    }

    /**
     * DTO의 targetType 문자열을 protobuf TargetType Enum으로 매핑한다.
     * 기존 V1 payload처럼 targetType이 없으면 대상 없음으로 처리한다.
     */
    private TargetType mapToTargetType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return TargetType.TARGET_TYPE_UNSPECIFIED;
        }
        try {
            return TargetType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            String formatted = "TARGET_TYPE_" + typeStr.toUpperCase();
            try {
                return TargetType.valueOf(formatted);
            } catch (Exception ex) {
                return TargetType.TARGET_TYPE_UNSPECIFIED;
            }
        }
    }
}
