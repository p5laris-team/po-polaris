package p5laris.item.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.UserItem;

import java.util.List;
import java.util.Optional;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {
    Optional<UserItem> findByUserIdAndItemId(Long userId, Long itemId);
    List<UserItem> findByUserId(Long userId);
    List<UserItem> findByUserIdAndItemItemType(Long userId, String itemType);
    
    // For cursor-based paging on user items
    List<UserItem> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long id, Pageable pageable);
    List<UserItem> findByUserIdAndItemItemTypeAndIdGreaterThanOrderByIdAsc(Long userId, String itemType, Long id, Pageable pageable);
}
