package p5laris.item.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.UserItemPurchase;

import java.util.Optional;

public interface UserItemPurchaseRepository extends JpaRepository<UserItemPurchase, Long> {

    /** 멱등키로 기존 구매 이력을 조회한다. (LazyInitializationException 방지를 위해 UserItem과 Item을 페치 조인) */
    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserItemPurchase p JOIN FETCH p.userItem ui JOIN FETCH ui.item WHERE p.idempotencyKey = :idempotencyKey")
    Optional<UserItemPurchase> findByIdempotencyKey(@org.springframework.data.repository.query.Param("idempotencyKey") String idempotencyKey);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserItemPurchase p WHERE p.status = 'UNKNOWN' AND p.nextAttemptAt <= :now ORDER BY p.nextAttemptAt ASC")
    java.util.List<UserItemPurchase> findUnknownPurchases(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
