package p5laris.ai.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface CharacterTalkMessageRepository extends JpaRepository<CharacterTalkMessage, Long> {

    @Query("""
            select message
            from CharacterTalkMessage message
            where message.session.id = :sessionId
            order by message.sequence desc
            """)
    List<CharacterTalkMessage> findRecentMessages(@Param("sessionId") Long sessionId, Pageable pageable);

    @Query("""
            select message
            from CharacterTalkMessage message
            where message.session.id = :sessionId
            order by message.sequence asc
            """)
    List<CharacterTalkMessage> findBySessionIdOrderBySequenceAsc(@Param("sessionId") Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CharacterTalkMessage message where message.createdAt < :cutoff")
    int deleteMessagesBefore(@Param("cutoff") LocalDateTime cutoff);
}
