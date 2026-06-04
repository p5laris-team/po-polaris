package p5laris.user.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.StarPieceTransaction;

import java.util.List;
import java.util.Optional;

public interface StarPieceTransactionRepository extends JpaRepository<StarPieceTransaction, Long> {

    Optional<StarPieceTransaction> findByIdempotencyKey(String idempotencyKey);

    List<StarPieceTransaction> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);
}
