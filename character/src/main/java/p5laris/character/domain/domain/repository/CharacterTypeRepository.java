package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterType;

import java.util.List;
import java.util.Optional;

public interface CharacterTypeRepository extends JpaRepository<CharacterType, Long> {

    /** 코드로 캐릭터 타입 조회 (NOVA / MUMU / JJORY) */
    Optional<CharacterType> findByCode(String code);

    /** 활성 캐릭터 타입을 노출 순서 기준으로 조회 */
    List<CharacterType> findByActiveTrueOrderBySortOrderAsc();
}
