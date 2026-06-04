package p5laris.notification.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;

public interface NotificationPushDeliveryRepository extends JpaRepository<NotificationPushDelivery, Long> {
}