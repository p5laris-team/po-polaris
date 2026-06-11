package p5laris.notification.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.notification.domain.domain.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 ID만으로 찾지 않고 userId를 함께 확인해 다른 유저의 알림 읽음 처리를 막는다.
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    // cursor가 없고 read 필터도 없을 때 최신 알림 목록을 조회한다.
    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    // cursor가 있고 read 필터가 없을 때 cursor 이전 알림 목록을 조회한다.
    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    // cursor가 없고 read 필터가 있을 때 최신 알림 목록을 조회한다.
    List<Notification> findByUserIdAndReadOrderByIdDesc(Long userId, boolean read, Pageable pageable);

    // cursor와 read 필터가 모두 있을 때 cursor 이전 알림 목록을 조회한다.
    List<Notification> findByUserIdAndReadAndIdLessThanOrderByIdDesc(
            Long userId,
            boolean read,
            Long id,
            Pageable pageable
    );

    // 홈 화면 unreadCount 표시용이다.
    long countByUserIdAndReadFalse(Long userId);

    // 사용자의 안 읽은 알림만 한 번에 읽음 처리한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.read = true,
                   n.readAt = :readAt,
                   n.updatedAt = :updatedAt
             where n.userId = :userId
               and n.read = false
            """)
    int markAllReadByUserId(
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
