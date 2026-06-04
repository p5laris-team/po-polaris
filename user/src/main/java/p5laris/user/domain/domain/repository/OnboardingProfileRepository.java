package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.OnboardingProfile;

import java.util.Optional;

public interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, Long> {
    Optional<OnboardingProfile> findByUserId(Long userId);
}
