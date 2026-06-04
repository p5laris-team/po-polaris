package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByRefreshToken(String refreshToken);

    org.springframework.data.domain.Page<User> findByStatus(String status, org.springframework.data.domain.Pageable pageable);
}
