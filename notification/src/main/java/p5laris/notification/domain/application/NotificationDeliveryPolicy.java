package p5laris.notification.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.notification.domain.domain.entity.NotificationSetting;
import p5laris.notification.domain.domain.enums.NotificationType;

import java.time.Clock;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryPolicy {

    private static final String PUSH_DISABLED = "PUSH_DISABLED";
    private static final String MISSION_OFFER_DISABLED = "MISSION_OFFER_DISABLED";
    private static final String CHARACTER_STATE_DISABLED = "CHARACTER_STATE_DISABLED";
    private static final String DAILY_REMINDER_DISABLED = "DAILY_REMINDER_DISABLED";
    private static final String QUIET_HOURS = "QUIET_HOURS";

    private final Clock notificationClock;

    /**
     * 알림 row는 남기되, 사용자 설정에 따라 FCM 발송만 건너뛸지 결정한다.
     */
    public NotificationDeliveryDecision decide(NotificationSetting setting, NotificationType notificationType) {
        if (!setting.isPushEnabled()) {
            return NotificationDeliveryDecision.skipped(PUSH_DISABLED, "User disabled push notifications");
        }

        NotificationDeliveryDecision typeDecision = decideByNotificationType(setting, notificationType);
        if (!typeDecision.sendable()) {
            return typeDecision;
        }

        if (isQuietHoursActive(setting)) {
            return NotificationDeliveryDecision.skipped(QUIET_HOURS, "Quiet hours are active");
        }

        return NotificationDeliveryDecision.allowed();
    }

    private NotificationDeliveryDecision decideByNotificationType(
            NotificationSetting setting,
            NotificationType notificationType
    ) {
        if (notificationType == NotificationType.MISSION && !setting.isMissionOfferEnabled()) {
            return NotificationDeliveryDecision.skipped(MISSION_OFFER_DISABLED, "User disabled mission offer notifications");
        }

        if (notificationType == NotificationType.CARE && !setting.isCharacterStateEnabled()) {
            return NotificationDeliveryDecision.skipped(CHARACTER_STATE_DISABLED, "User disabled character state notifications");
        }

        if (notificationType == NotificationType.ATTENDANCE && !setting.isDailyReminderEnabled()) {
            return NotificationDeliveryDecision.skipped(DAILY_REMINDER_DISABLED, "User disabled daily reminder notifications");
        }

        return NotificationDeliveryDecision.allowed();
    }

    private boolean isQuietHoursActive(NotificationSetting setting) {
        if (!setting.isQuietHoursEnabled()) {
            return false;
        }

        LocalTime start = setting.getQuietHoursStart();
        LocalTime end = setting.getQuietHoursEnd();
        if (start == null || end == null || start.equals(end)) {
            return false;
        }

        LocalTime now = LocalTime.now(notificationClock);
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }

        return !now.isBefore(start) || now.isBefore(end);
    }
}
