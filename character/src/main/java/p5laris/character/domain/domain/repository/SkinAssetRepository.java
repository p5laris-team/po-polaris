package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.SkinAsset;

import java.util.List;

public interface SkinAssetRepository extends JpaRepository<SkinAsset, Long> {

    List<SkinAsset> findByItemIdAndCharacterTypeId(Long itemId, Long characterTypeId);
}
