package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD 1.3 character_assets
 * 캐릭터 타입별 이미지 에셋을 저장한다.
 */
@Entity
@Table(name = "character_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_type_id", nullable = false)
    private CharacterType characterType;

    /** 이미지 타입 (기본, 기쁨, 슬픔 등) */
    @Column(nullable = false)
    private String assetType;

    /** 캐릭터 이미지 URL */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String assetUrl;

    @Builder
    private CharacterAsset(CharacterType characterType, String assetType, String assetUrl) {
        this.characterType = characterType;
        this.assetType = assetType;
        this.assetUrl = assetUrl;
    }
}
