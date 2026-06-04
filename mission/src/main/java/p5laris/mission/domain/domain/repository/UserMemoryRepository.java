package p5laris.mission.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;

import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    // AI context에는 최근 기억 일부만 넣어 토큰 비용과 조회 비용을 제한한다.
    List<UserMemory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<UserMemory> findBySourceTypeAndSourceIdAndMemoryType(
            UserMemorySourceType sourceType,
            Long sourceId,
            UserMemoryType memoryType
    );
}
