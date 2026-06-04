package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ERD 1.3 character_types
 * MVP 캐릭터 3종(NOVA, MUMU, JJORY)의 기본 정보를 저장한다.
 */
@Entity
@Table(
        name = "character_types",
        indexes = {
                @Index(name = "idx_character_types_active_sort_order", columnList = "active, sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NOVA / MUMU / JJORY */
    @Column(nullable = false, unique = true)
    private String code;

    /** 노바 / 무무 / 쪼리 */
    @Column(nullable = false)
    private String name;

    /** 선택 화면 한 줄 소개 */
    @Column(nullable = false)
    private String summary;

    /** 성격 설명 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String personality;

    /** 말투 설명 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String speechStyle;

    /** 선택 화면 소개 문구 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String introMessage;

    /** 대표 대사 */
    @Column(nullable = false)
    private String sampleLine;

    /** 활성 여부 */
    @Column(nullable = false)
    private boolean active;

    /** 노출 순서 */
    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private CharacterType(String code, String name, String summary, String personality,
                          String speechStyle, String introMessage, String sampleLine,
                          boolean active, int sortOrder) {
        this.code = code;
        this.name = name;
        this.summary = summary;
        this.personality = personality;
        this.speechStyle = speechStyle;
        this.introMessage = introMessage;
        this.sampleLine = sampleLine;
        this.active = active;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
