package p5laris.item.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.UserItemUsage;

import java.util.Optional;

public interface UserItemUsageRepository extends JpaRepository<UserItemUsage, Long> {

    /** 멱등키로 기존 사용 이력을 조회한다. */
    Optional<UserItemUsage> findByIdempotencyKey(String idempotencyKey);
}
