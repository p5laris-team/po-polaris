package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.PaymentOrder;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderNo(String orderNo);
}
