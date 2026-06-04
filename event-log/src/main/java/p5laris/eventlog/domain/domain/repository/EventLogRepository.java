package p5laris.eventlog.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.eventlog.domain.domain.entity.EventLog;
import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    boolean existsByEventId(UUID eventId);
}

