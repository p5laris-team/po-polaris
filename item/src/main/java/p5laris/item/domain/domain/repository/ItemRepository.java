package p5laris.item.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.Item;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByActive(boolean active);
    List<Item> findByItemTypeAndActive(String itemType, boolean active);
    
    // For cursor-based paging
    List<Item> findByIdGreaterThanAndActiveOrderByIdAsc(Long id, boolean active, Pageable pageable);
    List<Item> findByIdGreaterThanAndItemTypeAndActiveOrderByIdAsc(Long id, String itemType, boolean active, Pageable pageable);

    List<Item> findByNameStartingWithAndCharacterTypeId(String prefix, Long characterTypeId);
}
