package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "share_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "headline", length = 100)
    private String headline;

    @Column(name = "share_url", nullable = false)
    private String shareUrl;

    @Builder
    private ShareCard(Long userId, Long characterId, String imageUrl, String headline, String shareUrl) {
        this.userId = userId;
        this.characterId = characterId;
        this.imageUrl = imageUrl;
        this.headline = headline;
        this.shareUrl = shareUrl;
    }

    public void updateImageKey(String imageKey) {
        this.imageUrl = imageKey;
    }

    public void updateGeneratedCard(String imageKey, String headline) {
        this.imageUrl = imageKey;
        this.headline = headline;
    }
}
