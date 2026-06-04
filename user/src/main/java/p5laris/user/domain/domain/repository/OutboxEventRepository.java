package p5laris.user.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.user.domain.domain.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.nextAttemptAt <= :now ORDER BY o.nextAttemptAt ASC")
    List<OutboxEvent> findPendingEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'SUCCEEDED' AND o.updatedAt <= :threshold")
    void deleteSucceededEvents(@Param("threshold") LocalDateTime threshold);

    long countByStatus(String status);
}
