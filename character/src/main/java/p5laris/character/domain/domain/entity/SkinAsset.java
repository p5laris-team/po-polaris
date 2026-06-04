package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skin_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_type_id", nullable = false)
    private CharacterType characterType;

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(name = "asset_url", nullable = false, columnDefinition = "TEXT")
    private String assetUrl;

    @Builder
    private SkinAsset(Long itemId, CharacterType characterType, String assetType, String assetUrl) {
        this.itemId = itemId;
        this.characterType = characterType;
        this.assetType = assetType;
        this.assetUrl = assetUrl;
    }
}
