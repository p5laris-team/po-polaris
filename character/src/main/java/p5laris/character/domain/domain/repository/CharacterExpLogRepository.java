package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterExpLog;
import p5laris.character.domain.domain.enums.CharacterExpSourceType;

import java.util.Optional;

public interface CharacterExpLogRepository extends JpaRepository<CharacterExpLog, Long> {

    Optional<CharacterExpLog> findByIdempotencyKey(String idempotencyKey);

    Optional<CharacterExpLog> findBySourceTypeAndSourceId(CharacterExpSourceType sourceType, Long sourceId);
}
