package p5laris.user.domain.application;

import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.NotificationType;
import com.p5laris.proto.notification.v1.SendPushNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;
import p5laris.user.domain.application.event.NotificationRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceNotificationScheduler {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // 매일 오전 0시에 실행 (초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void sendDailyAttendanceReminder() {
        log.info("Starting daily attendance reminder scheduler...");

        int pageNumber = 0;
        int pageSize = 500;
        long totalSentCount = 0;

        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            Page<User> activeUsersPage = userRepository.findByStatus("ACTIVE", pageable);

            if (activeUsersPage.isEmpty()) {
                break;
            }

            for (User user : activeUsersPage.getContent()) {
                try {
                    NotificationRequestEvent event = new NotificationRequestEvent(
                            user.getId(),
                            "출석 체크 시간!",
                            "새로운 하루가 시작되었습니다. 잊지 말고 오늘의 출석 보상을 받아가세요!",
                            "NOTIFICATION_TYPE_ATTENDANCE"
                    );

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .aggregateType("NOTIFICATION_REQUEST")
                            .aggregateId(user.getId())
                            .eventType("PUSH_NOTIFICATION")
                            .payload(objectMapper.writeValueAsString(event))
                            .idempotencyKey("ATTENDANCE_NOTI_" + user.getId() + "_" + java.time.LocalDate.now())
                            .status("PENDING")
                            .nextAttemptAt(java.time.LocalDateTime.now())
                            .build();

                    outboxEventRepository.save(outboxEvent);
                    totalSentCount++;
                } catch (Exception e) {
                    log.error("Failed to request push notification for user: {}", user.getId(), e);
                }
            }

            if (activeUsersPage.isLast()) {
                break;
            }
            pageNumber++;
        }

        log.info("Finished daily attendance reminder scheduler. Total requests sent: {}", totalSentCount);
    }
}
