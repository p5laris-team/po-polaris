package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.ShareCard;

import java.util.Optional;

public interface ShareCardRepository extends JpaRepository<ShareCard, Long> {

    /** Finds share card by userId and characterId (for idempotent creation). */
    Optional<ShareCard> findByUserIdAndCharacterId(Long userId, Long characterId);

    /** Finds share card by image key (for retry-safe immutable creation). */
    Optional<ShareCard> findByUserIdAndImageUrl(Long userId, String imageUrl);

    /** Finds share card by its share URL (for public share link lookup). */
    Optional<ShareCard> findByShareUrl(String shareUrl);
}
