package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterStoryFragment;
import p5laris.character.domain.domain.enums.CharacterStoryFragmentType;
import p5laris.character.domain.domain.enums.CharacterStoryTriggerType;

import java.util.List;

public interface CharacterStoryFragmentRepository extends JpaRepository<CharacterStoryFragment, Long> {

    /**
     * 현재 캐릭터 타입과 COMMON 후보 중에서 요청 트리거/레벨 조건에 맞는 활성 조각을 조회한다.
     *
     * seed 수가 수백 개 수준이라 캐시는 두지 않고, 인덱스로 후보 범위만 좁힌다.
     */
    List<CharacterStoryFragment> findByActiveTrueAndCharacterTypeCodeInAndTriggerTypeAndMinLevelLessThanEqualOrderBySortOrderAscIdAsc(
            List<String> characterTypeCodes,
            CharacterStoryTriggerType triggerType,
            int minLevel
    );

    long countByActiveTrueAndCharacterTypeCodeInAndFragmentTypeIn(
            List<String> characterTypeCodes,
            List<CharacterStoryFragmentType> fragmentTypes
    );

    List<CharacterStoryFragment> findByActiveTrueAndCharacterTypeCodeInAndFragmentTypeInOrderByMinLevelAscSortOrderAscIdAsc(
            List<String> characterTypeCodes,
            List<CharacterStoryFragmentType> fragmentTypes
    );
}
