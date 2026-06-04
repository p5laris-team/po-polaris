package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.enums.ActionType;

import java.time.Instant;
import java.util.List;

public interface CharacterCareLogRepository extends JpaRepository<CharacterCareLog, Long> {

    /** 캐릭터의 돌봄 로그를 최신순으로 조회 */
    List<CharacterCareLog> findByCharacterIdOrderByCreatedAtDesc(Long characterId);

    /** 멱등키로 로그 조회 */
    java.util.Optional<CharacterCareLog> findByIdempotencyKey(String idempotencyKey);

    /** 하루 돌봄 경험치 인정 횟수 계산용 로그 조회 */
    List<CharacterCareLog> findByCharacterIdAndActionTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long characterId,
            ActionType actionType,
            Instant startAt,
            Instant endAt
    );
}
