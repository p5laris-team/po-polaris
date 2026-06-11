package p5laris.notification.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import p5laris.notification.domain.domain.entity.NotificationPushDelivery;
import p5laris.notification.domain.domain.enums.PushDeliveryStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationPushDeliveryRepository extends JpaRepository<NotificationPushDelivery, Long> {

    @Query("""
            select d
              from NotificationPushDelivery d
             where d.notificationId = :notificationId
               and d.deliveryStatus = :status
               and (d.nextAttemptAt is null or d.nextAttemptAt <= :now)
             order by d.id asc
            """)
    List<NotificationPushDelivery> findDueByNotificationId(
            @Param("notificationId") Long notificationId,
            @Param("status") PushDeliveryStatus status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select d
              from NotificationPushDelivery d
             where d.deliveryStatus = :status
               and (d.nextAttemptAt is null or d.nextAttemptAt <= :now)
             order by d.id asc
            """)
    List<NotificationPushDelivery> findDue(
            @Param("status") PushDeliveryStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationPushDelivery d
               set d.attemptCount = d.attemptCount + 1,
                   d.attemptedAt = :attemptedAt,
                   d.nextAttemptAt = :nextAttemptAt
             where d.id = :deliveryId
               and d.deliveryStatus = :status
               and (d.nextAttemptAt is null or d.nextAttemptAt <= :attemptedAt)
            """)
    int reservePendingDelivery(
            @Param("deliveryId") Long deliveryId,
            @Param("status") PushDeliveryStatus status,
            @Param("attemptedAt") LocalDateTime attemptedAt,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt
    );
}
