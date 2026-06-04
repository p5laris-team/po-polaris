package p5laris.character.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.character.domain.domain.entity.ShareLog;

import java.time.LocalDate;
import java.util.Optional;

public interface ShareLogRepository extends JpaRepository<ShareLog, Long> {

    /**
     * 멱등성 키로 공유 로그 조회.
     * 형식: SHARE_REWARD:{userId}:{shareDate}
     */
    Optional<ShareLog> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l
            from ShareLog l
            where l.id = :id
            """)
    Optional<ShareLog> findByIdForUpdate(@Param("id") Long id);

    /**
     * 사용자의 특정 날짜 공유 보상 지급 여부 확인.
     * 공유 보상은 하루 1회만 지급한다 (AGENTS.md §20.5).
     */
    boolean existsByUserIdAndShareDateAndRewardPaidTrue(Long userId, LocalDate shareDate);

    Optional<ShareLog> findTopByUserIdAndShareDateOrderBySharedAtDesc(Long userId, LocalDate shareDate);
}
