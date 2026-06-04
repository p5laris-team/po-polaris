package p5laris.notification.domain.application;

import org.junit.jupiter.api.Test;
import p5laris.notification.domain.domain.entity.NotificationSetting;
import p5laris.notification.domain.domain.enums.NotificationType;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryPolicyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 미션_제안_알림을_꺼두면_MISSION_푸시를_건너뛴다() {
        NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(fixedClock(12, 0));
        NotificationSetting setting = defaultSetting();
        setting.updateNotificationOptions(
                true,
                false,
                true,
                true,
                false,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0)
        );

        NotificationDeliveryDecision decision = policy.decide(setting, NotificationType.MISSION);

        assertThat(decision.sendable()).isFalse();
        assertThat(decision.skipCode()).isEqualTo("MISSION_OFFER_DISABLED");
    }

    @Test
    void 방해금지_시간이_자정을_넘어도_현재_시간이_범위_안이면_푸시를_건너뛴다() {
        NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(fixedClock(23, 30));
        NotificationSetting setting = defaultSetting();
        setting.updateNotificationOptions(
                true,
                true,
                true,
                true,
                true,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0)
        );

        NotificationDeliveryDecision decision = policy.decide(setting, NotificationType.CARE);

        assertThat(decision.sendable()).isFalse();
        assertThat(decision.skipCode()).isEqualTo("QUIET_HOURS");
    }

    @Test
    void 설정과_방해금지_시간에_걸리지_않으면_푸시를_보낼_수_있다() {
        NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(fixedClock(12, 0));
        NotificationSetting setting = defaultSetting();

        NotificationDeliveryDecision decision = policy.decide(setting, NotificationType.ATTENDANCE);

        assertThat(decision.sendable()).isTrue();
        assertThat(decision.skipCode()).isNull();
    }

    private NotificationSetting defaultSetting() {
        return NotificationSetting.defaultSetting(1L);
    }

    private Clock fixedClock(int hour, int minute) {
        return Clock.fixed(
                LocalDateTime.of(2026, 5, 26, hour, minute)
                        .atZone(SEOUL)
                        .toInstant(),
                SEOUL
        );
    }
}
