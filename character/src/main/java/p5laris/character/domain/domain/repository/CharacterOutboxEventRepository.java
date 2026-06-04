package p5laris.character.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.character.domain.domain.entity.CharacterOutboxEvent;
import p5laris.character.domain.domain.enums.CharacterOutboxEventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CharacterOutboxEventRepository extends JpaRepository<CharacterOutboxEvent, Long> {

    Optional<CharacterOutboxEvent> findByIdempotencyKey(String idempotencyKey);

    Optional<CharacterOutboxEvent> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType,
            Long aggregateId,
            String eventType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from CharacterOutboxEvent o
            where o.id = :id
            """)
    Optional<CharacterOutboxEvent> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.id
            from CharacterOutboxEvent o
            where o.eventType = :eventType
            and (
                (
                    o.status = :pendingStatus
                    and o.nextAttemptAt <= :now
                ) or (
                    o.status = :processingStatus
                    and o.nextAttemptAt <= :now
                )
            )
            order by o.nextAttemptAt asc, o.id asc
            """)
    List<Long> findDispatchableIds(
            @Param("eventType") String eventType,
            @Param("pendingStatus") CharacterOutboxEventStatus pendingStatus,
            @Param("processingStatus") CharacterOutboxEventStatus processingStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    long countByStatus(CharacterOutboxEventStatus status);
}
