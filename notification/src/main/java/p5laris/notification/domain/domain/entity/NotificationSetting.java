package p5laris.notification.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.notification.core.entity.BaseEntity;

import java.time.LocalTime;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseEntity {

    private static final int DEFAULT_DAILY_PUSH_LIMIT = 3;
    private static final LocalTime DEFAULT_QUIET_HOURS_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_QUIET_HOURS_END = LocalTime.of(8, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "daily_push_limit", nullable = false)
    private int dailyPushLimit;

    @Column(name = "mission_offer_enabled", nullable = false)
    private boolean missionOfferEnabled;

    @Column(name = "character_state_enabled", nullable = false)
    private boolean characterStateEnabled;

    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start", nullable = false)
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end", nullable = false)
    private LocalTime quietHoursEnd;

    @Builder
    public NotificationSetting(
            Long userId,
            boolean pushEnabled,
            int dailyPushLimit,
            Boolean missionOfferEnabled,
            Boolean characterStateEnabled,
            Boolean dailyReminderEnabled,
            Boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
        this.userId = userId;
        this.pushEnabled = pushEnabled;
        this.dailyPushLimit = dailyPushLimit;
        this.missionOfferEnabled = missionOfferEnabled == null || missionOfferEnabled;
        this.characterStateEnabled = characterStateEnabled == null || characterStateEnabled;
        this.dailyReminderEnabled = dailyReminderEnabled == null || dailyReminderEnabled;
        this.quietHoursEnabled = quietHoursEnabled != null && quietHoursEnabled;
        this.quietHoursStart = quietHoursStart == null ? DEFAULT_QUIET_HOURS_START : quietHoursStart;
        this.quietHoursEnd = quietHoursEnd == null ? DEFAULT_QUIET_HOURS_END : quietHoursEnd;
    }

    public static NotificationSetting defaultSetting(Long userId) {
        return NotificationSetting.builder()
                .userId(userId)
                .pushEnabled(true)
                .dailyPushLimit(DEFAULT_DAILY_PUSH_LIMIT)
                .missionOfferEnabled(true)
                .characterStateEnabled(true)
                .dailyReminderEnabled(true)
                .quietHoursEnabled(false)
                .quietHoursStart(DEFAULT_QUIET_HOURS_START)
                .quietHoursEnd(DEFAULT_QUIET_HOURS_END)
                .build();
    }

    public void updatePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public void updateDailyPushLimit(int dailyPushLimit) {
        this.dailyPushLimit = dailyPushLimit;
    }

    public void updateNotificationOptions(
            boolean pushEnabled,
            boolean missionOfferEnabled,
            boolean characterStateEnabled,
            boolean dailyReminderEnabled,
            boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
        this.pushEnabled = pushEnabled;
        this.missionOfferEnabled = missionOfferEnabled;
        this.characterStateEnabled = characterStateEnabled;
        this.dailyReminderEnabled = dailyReminderEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
    }
}
