package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterAsset;
import p5laris.character.domain.domain.entity.CharacterType;

import java.util.List;

public interface CharacterAssetRepository extends JpaRepository<CharacterAsset, Long> {

    /** Find all assets belonging to the given character type (by FK id) */
    List<CharacterAsset> findByCharacterTypeId(Long characterTypeId);

    /** Find all assets belonging to the given CharacterType entity */
    List<CharacterAsset> findByCharacterType(CharacterType characterType);

    /** Find assets by character type id and asset type */
    List<CharacterAsset> findByCharacterTypeIdAndAssetType(Long characterTypeId, String assetType);
}
