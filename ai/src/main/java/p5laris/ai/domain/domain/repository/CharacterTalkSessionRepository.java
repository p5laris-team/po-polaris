package p5laris.ai.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkSessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CharacterTalkSessionRepository extends JpaRepository<CharacterTalkSession, Long> {

    Optional<CharacterTalkSession> findBySessionId(String sessionId);

    Optional<CharacterTalkSession> findFirstByUserIdAndCharacterIdAndStatusAndExpiresAtAfterOrderByLastMessageAtDesc(
            Long userId,
            Long characterId,
            CharacterTalkSessionStatus status,
            LocalDateTime now
    );

    List<CharacterTalkSession> findTop10ByUserIdAndCharacterIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            Long userId,
            Long characterId,
            CharacterTalkSessionStatus status,
            LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CharacterTalkSession session
            where session.status <> :activeStatus
              and session.updatedAt < :cutoff
            """)
    int deleteClosedSessionsBefore(
            @Param("activeStatus") CharacterTalkSessionStatus activeStatus,
            @Param("cutoff") LocalDateTime cutoff
    );
}
