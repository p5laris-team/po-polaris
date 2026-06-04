package p5laris.mission.domain.application.guard;

import org.junit.jupiter.api.Test;
import p5laris.mission.domain.application.diversity.MissionDiversitySnapshot;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissionAiCandidateGuardTest {

    private final MissionAiCandidateGuard guard = new MissionAiCandidateGuard();

    @Test
    void fallback_카테고리가_허용되면_AI_후보도_같은_카테고리만_확정한다() {
        AiMissionCandidateGuardResult result = guard.validate(request(
                MissionCategoryType.BASIC_ROUTINE,
                candidate("BASIC_ROUTINE", "EASY")
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.candidate()).hasValueSatisfying(candidate ->
                assertThat(candidate.category()).isEqualTo(MissionCategoryType.BASIC_ROUTINE)
        );
    }

    @Test
    void fallback_카테고리가_허용되는데_AI가_다른_카테고리를_주면_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(request(
                MissionCategoryType.BASIC_ROUTINE,
                candidate("REST_RECOVERY", "EASY")
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason())
                .contains(AiMissionCandidateRejectionReason.CATEGORY_CHANGED_WITHOUT_POLICY_REASON);
    }

    @Test
    void fallback_카테고리가_시간대에_막히면_허용되는_카테고리_변경은_받아들인다() {
        AiMissionCandidateGuardResult result = guard.validate(new AiMissionCandidateGuardRequest(
                1L,
                10L,
                MissionCategoryType.OUTDOOR_LIGHT,
                LocalDateTime.of(2026, 5, 30, 23, 0),
                false,
                candidate("REST_RECOVERY", "EASY")
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.candidate()).hasValueSatisfying(candidate ->
                assertThat(candidate.category()).isEqualTo(MissionCategoryType.REST_RECOVERY)
        );
    }

    @Test
    void 시간대에_막힌_카테고리는_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(new AiMissionCandidateGuardRequest(
                1L,
                10L,
                MissionCategoryType.OUTDOOR_LIGHT,
                LocalDateTime.of(2026, 5, 30, 23, 0),
                false,
                candidate("OUTDOOR_LIGHT", "EASY")
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason()).contains(AiMissionCandidateRejectionReason.BLOCKED_CATEGORY);
    }

    @Test
    void challenge는_구체적인_수행량이_없으면_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(request(
                MissionCategoryType.BASIC_ROUTINE,
                new AiMissionTextResult(
                        1L,
                        "작은 도전 루틴",
                        "평소보다 조금 더 집중해서 루틴을 해보세요.",
                        "좋아요, 오늘은 조금 더 단단하게 가볼게요.",
                        "해보고 나니 어땠나요?",
                        "도전한 마음 자체가 반짝였어요.",
                        "BASIC_ROUTINE",
                        "CHALLENGE",
                        false,
                        "request-1"
                )
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason()).contains(AiMissionCandidateRejectionReason.INVALID_CHALLENGE_VOLUME);
    }

    @Test
    void 새벽에는_대청소처럼_자극이_큰_키워드를_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(new AiMissionCandidateGuardRequest(
                1L,
                10L,
                MissionCategoryType.BASIC_ROUTINE,
                LocalDateTime.of(2026, 5, 30, 1, 0),
                false,
                new AiMissionTextResult(
                        1L,
                        "방 대청소 시작하기",
                        "지금 방 청소를 크게 시작해서 눈에 보이는 구역을 정리해보세요.",
                        "새벽에는 조용히 움직여볼게요.",
                        "해보고 나니 어땠나요?",
                        "작게 정리한 것도 충분해요.",
                        "BASIC_ROUTINE",
                        "EASY",
                        false,
                        "request-1"
                )
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason()).contains(AiMissionCandidateRejectionReason.BLOCKED_KEYWORD);
    }

    @Test
    void 오늘_이미_같은_제목을_본_미션은_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(new AiMissionCandidateGuardRequest(
                1L,
                10L,
                MissionCategoryType.BASIC_ROUTINE,
                LocalDateTime.of(2026, 5, 30, 14, 0),
                false,
                List.of(snapshot(1L, "물 한 모금 마시기", "가까운 물을 마셔요")),
                candidate("BASIC_ROUTINE", "EASY")
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason()).contains(AiMissionCandidateRejectionReason.DUPLICATE_TITLE);
    }

    @Test
    void 오늘_이미_같은_행동군을_본_미션은_거절한다() {
        AiMissionCandidateGuardResult result = guard.validate(new AiMissionCandidateGuardRequest(
                1L,
                10L,
                MissionCategoryType.BASIC_ROUTINE,
                LocalDateTime.of(2026, 5, 30, 14, 0),
                false,
                List.of(snapshot(1L, "수분 채우기", "물을 한 잔 마셔보세요.")),
                new AiMissionTextResult(
                        1L,
                        "따뜻한 차 한 모금",
                        "가까운 컵에 담긴 음료를 한 모금 마셔보세요.",
                        "좋아요, 작게 시작해볼게요.",
                        "해보고 나니 어땠나요?",
                        "작은 루틴도 충분히 반짝였어요.",
                        "BASIC_ROUTINE",
                        "EASY",
                        false,
                        "request-1"
                )
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectionReason()).contains(AiMissionCandidateRejectionReason.DUPLICATE_ACTION_FAMILY);
    }

    private AiMissionCandidateGuardRequest request(
            MissionCategoryType fallbackCategory,
            AiMissionTextResult candidate
    ) {
        return new AiMissionCandidateGuardRequest(
                1L,
                10L,
                fallbackCategory,
                LocalDateTime.of(2026, 5, 30, 14, 0),
                false,
                candidate
        );
    }

    private AiMissionTextResult candidate(String category, String difficulty) {
        return new AiMissionTextResult(
                1L,
                "물 한 모금 마시기",
                "가까운 물을 한 모금 마시고 잠깐 숨을 골라보세요.",
                "좋아요, 작게 시작해볼게요.",
                "해보고 나니 어땠나요?",
                "작은 루틴도 충분히 반짝였어요.",
                category,
                difficulty,
                false,
                "request-1"
        );
    }

    private MissionDiversitySnapshot snapshot(Long missionId, String title, String description) {
        return new MissionDiversitySnapshot(
                missionId,
                title,
                description,
                MissionCategoryType.BASIC_ROUTINE,
                UserMissionStatus.COMPLETED,
                null
        );
    }
}
