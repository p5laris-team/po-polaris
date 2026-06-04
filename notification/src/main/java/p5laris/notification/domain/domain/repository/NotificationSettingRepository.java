package p5laris.notification.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.notification.domain.domain.entity.NotificationSetting;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    // 알림 설정은 사용자당 하나만 사용한다.
    Optional<NotificationSetting> findByUserId(Long userId);
}