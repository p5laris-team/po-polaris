package p5laris.notification.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;

import java.util.List;
import java.util.Optional;

public interface FcmDeviceTokenRepository extends JpaRepository<FcmDeviceToken, Long> {

    // 같은 FCM token이 중복 저장되지 않도록 tokenHash 기준으로 조회한다.
    Optional<FcmDeviceToken> findByTokenHash(String tokenHash);

    // 특정 사용자에게 활성 상태인 FCM token 목록을 조회한다.
    List<FcmDeviceToken> findByUserIdAndActiveTrue(Long userId);
}