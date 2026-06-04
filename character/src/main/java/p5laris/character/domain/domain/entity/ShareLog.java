package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ERD 1.5 share_logs
 * SNS 공유 이벤트 로그와 공유 보상 정보를 저장한다.
 *
 * 정책 (AGENTS.md §20.5):
 * - 공유 보상은 하루 1회만 지급한다.
 * - 중복 보상 방지를 위해 idempotency_key를 사용한다.
 */
@Entity
@Table(
        name = "share_logs",
        indexes = {
                @Index(name = "idx_share_logs_user_id_share_date", columnList = "user_id, share_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 캐릭터 ID */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** 공유 카드 ID */
    @Column(name = "share_card_id", nullable = false)
    private Long shareCardId;

    /** 공유 타입 */
    @Column(name = "share_type", nullable = false)
    private String shareType;

    /** 공유 플랫폼 */
    @Column(name = "platform", nullable = false)
    private String platform;

    /** 공유 시각 */
    @Column(name = "shared_at", nullable = false)
    private Instant sharedAt;

    /** 보상 제한 기준일 (SHARE_REWARD:{userId}:{shareDate} idempotency 기준) */
    @Column(name = "share_date", nullable = false)
    private LocalDate shareDate;

    /** 공유 보상 별조각 */
    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    /** 보상 수령 여부 */
    @Column(name = "reward_paid", nullable = false)
    private boolean rewardPaid;

    /**
     * 공유 보상 중복 방지 키.
     * 형식: SHARE_REWARD:{userId}:{shareDate}
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Builder
    private ShareLog(Long userId, Long characterId, Long shareCardId,
                     String shareType, String platform, Instant sharedAt,
                     LocalDate shareDate, int rewardStarPiece, boolean rewardPaid,
                     String idempotencyKey) {
        this.userId = userId;
        this.characterId = characterId;
        this.shareCardId = shareCardId;
        this.shareType = shareType;
        this.platform = platform;
        this.sharedAt = sharedAt;
        this.shareDate = shareDate;
        this.rewardStarPiece = rewardStarPiece;
        this.rewardPaid = rewardPaid;
        this.idempotencyKey = idempotencyKey;
    }

    public void markRewardPaid() {
        this.rewardPaid = true;
    }
}
