package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.item.core.entity.BaseEntity;

/**
 * 아이템 사용 이력 엔티티.
 *
 * character 모듈에서 돌봄 액션(FEED / SLEEP / PLAY) 시 소모성(CONSUMABLE) 아이템을 사용할 때마다
 * item 모듈의 UseItem gRPC API를 통해 이 테이블에 이력이 기록된다.
 *
 * idempotencyKey: 동일 요청이 네트워크 재시도 등으로 중복 도달해도 한 번만 처리되도록 보장한다.
 */
@Entity
@Table(name = "item_usage_histories")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserItemUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 아이템을 사용한 유저 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 사용된 UserItem 행 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false)
    private UserItem userItem;

    /** 사용된 아이템 ID (편의 컬럼 — JOIN 없이 조회 가능) */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 사용 수량 (MVP = 1) */
    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    /**
     * 어떤 컨텍스트에서 사용됐는지 (예: "CARE_ACTION").
     * 향후 다양한 도메인에서 아이템을 사용할 때 확장 포인트로 활용한다.
     */
    @Column(name = "ref_type", length = 50)
    private String refType;

    /** 해당 컨텍스트 PK (예: care_log_id) */
    @Column(name = "ref_id")
    private Long refId;

    /**
     * 멱등키: gRPC 클라이언트(character 모듈)가 생성해 전달한다.
     * UNIQUE 제약으로 DB 레벨에서 중복 삽입을 방지한다.
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;
}
