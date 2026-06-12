package p5laris.mission.domain.application.time;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.enums.MissionCategoryType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTimePolicyTest {

    @Test
    void 시간대를_KST_hour_기준으로_분류한다() {
        assertThat(MissionTimeSlot.fromHour(0)).isEqualTo(MissionTimeSlot.LATE_NIGHT);
        assertThat(MissionTimeSlot.fromHour(5)).isEqualTo(MissionTimeSlot.LATE_NIGHT);
        assertThat(MissionTimeSlot.fromHour(6)).isEqualTo(MissionTimeSlot.MORNING);
        assertThat(MissionTimeSlot.fromHour(10)).isEqualTo(MissionTimeSlot.MORNING);
        assertThat(MissionTimeSlot.fromHour(11)).isEqualTo(MissionTimeSlot.AFTERNOON);
        assertThat(MissionTimeSlot.fromHour(16)).isEqualTo(MissionTimeSlot.AFTERNOON);
        assertThat(MissionTimeSlot.fromHour(17)).isEqualTo(MissionTimeSlot.EVENING);
        assertThat(MissionTimeSlot.fromHour(20)).isEqualTo(MissionTimeSlot.EVENING);
        assertThat(MissionTimeSlot.fromHour(21)).isEqualTo(MissionTimeSlot.NIGHT);
        assertThat(MissionTimeSlot.fromHour(23)).isEqualTo(MissionTimeSlot.NIGHT);
    }

    @Test
    void 밤에는_햇빛과_OUTDOOR_LIGHT_후보를_허용하지_않는다() {
        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.NIGHT,
                MissionCategoryType.OUTDOOR_LIGHT,
                "짧은 햇빛 충전하기",
                "가능하다면 햇빛이나 밝은 곳에 1분 정도 머물러보세요."
        )).isFalse();

        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.NIGHT,
                MissionCategoryType.REST_RECOVERY,
                "잠들기 전 호흡 고르기",
                "자리에서 천천히 숨을 골라보세요."
        )).isTrue();
    }

    @Test
    void 새벽에는_SOCIAL_LIGHT와_강한_움직임_후보를_허용하지_않는다() {
        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.LATE_NIGHT,
                MissionCategoryType.SOCIAL_LIGHT,
                "친구에게 메시지 보내기",
                "생각나는 사람에게 짧게 안부 메시지를 보내보세요."
        )).isFalse();

        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.LATE_NIGHT,
                MissionCategoryType.BODY_CARE,
                "제자리 점프 10번",
                "제자리에서 가볍게 점프해보세요."
        )).isFalse();
    }

    @Test
    void 시간대_context에는_추천_카테고리와_차단_키워드가_들어간다() {
        assertThat(MissionTimePolicy.toContext(MissionTimeSlot.LATE_NIGHT))
                .containsEntry("currentTimeSlot", "LATE_NIGHT")
                .containsKey("recommendedCategories")
                .containsKey("blockedCategories")
                .containsKey("recommendedMissionTraits")
                .containsKey("blockedMissionKeywords");
    }

    @Test
    void 모든_시간대의_추천_카테고리와_차단_카테고리를_제공한다() {
        assertThat(MissionTimePolicy.recommendedCategories(MissionTimeSlot.LATE_NIGHT))
                .containsExactly(
                        MissionCategoryType.REST_RECOVERY,
                        MissionCategoryType.MIND_RECORD,
                        MissionCategoryType.BASIC_ROUTINE
                );
        assertThat(MissionTimePolicy.recommendedCategories(MissionTimeSlot.NIGHT))
                .containsExactly(
                        MissionCategoryType.REST_RECOVERY,
                        MissionCategoryType.MIND_RECORD,
                        MissionCategoryType.BASIC_ROUTINE,
                        MissionCategoryType.SPACE_RESET
                );
        assertThat(MissionTimePolicy.recommendedCategories(MissionTimeSlot.MORNING))
                .containsExactly(
                        MissionCategoryType.BASIC_ROUTINE,
                        MissionCategoryType.BODY_CARE,
                        MissionCategoryType.OUTDOOR_LIGHT,
                        MissionCategoryType.SPACE_RESET
                );
        assertThat(MissionTimePolicy.recommendedCategories(MissionTimeSlot.AFTERNOON))
                .containsExactly(
                        MissionCategoryType.BODY_CARE,
                        MissionCategoryType.SPACE_RESET,
                        MissionCategoryType.MIND_RECORD,
                        MissionCategoryType.SOCIAL_LIGHT
                );
        assertThat(MissionTimePolicy.recommendedCategories(MissionTimeSlot.EVENING))
                .containsExactly(
                        MissionCategoryType.REST_RECOVERY,
                        MissionCategoryType.MIND_RECORD,
                        MissionCategoryType.SPACE_RESET,
                        MissionCategoryType.BODY_CARE
                );

        assertThat(MissionTimePolicy.blockedCategories(MissionTimeSlot.LATE_NIGHT))
                .containsExactlyInAnyOrder(
                        MissionCategoryType.OUTDOOR_LIGHT,
                        MissionCategoryType.SOCIAL_LIGHT
                );
        assertThat(MissionTimePolicy.blockedCategories(MissionTimeSlot.NIGHT))
                .containsExactly(MissionCategoryType.OUTDOOR_LIGHT);
        assertThat(MissionTimePolicy.blockedCategories(MissionTimeSlot.MORNING)).isEmpty();
        assertThat(MissionTimePolicy.blockedCategories(MissionTimeSlot.AFTERNOON)).isEmpty();
        assertThat(MissionTimePolicy.blockedCategories(MissionTimeSlot.EVENING)).isEmpty();
    }

    @Test
    void 모든_시간대의_추천_특성과_context를_생성한다() {
        for (MissionTimeSlot timeSlot : MissionTimeSlot.values()) {
            assertThat(MissionTimePolicy.recommendedMissionTraits(timeSlot)).isNotEmpty();

            Map<String, Object> context = MissionTimePolicy.toContext(timeSlot);
            assertThat(context)
                    .containsEntry("currentTimeSlot", timeSlot.name())
                    .containsEntry(
                            "recommendedCategories",
                            MissionTimePolicy.recommendedCategories(timeSlot).stream()
                                    .map(Enum::name)
                                    .toList()
                    );
        }
    }

    @Test
    void 시간대가_없으면_카테고리와_텍스트에_관계없이_허용한다() {
        assertThat(MissionTimePolicy.isCandidateAllowed(
                null,
                MissionCategoryType.OUTDOOR_LIGHT,
                "햇빛 아래에서 달리기"
        )).isTrue();
    }

    @Test
    void null_template은_허용하지_않고_template의_모든_문구를_검사한다() {
        assertThat(MissionTimePolicy.isTemplateAllowed(MissionTimeSlot.NIGHT, null)).isFalse();

        MissionTemplate template = template(
                MissionCategoryType.REST_RECOVERY,
                "조용한 호흡",
                "가볍게 숨을 고르세요.",
                "천천히 시작해요.",
                "오늘 어땠나요?",
                "SUNLIGHT 아래에서 마무리해요."
        );

        assertThat(MissionTimePolicy.isTemplateAllowed(MissionTimeSlot.NIGHT, template)).isFalse();
        assertThat(MissionTimePolicy.isTemplateAllowed(MissionTimeSlot.MORNING, template)).isTrue();
    }

    @Test
    void 차단_키워드는_대소문자를_구분하지_않고_null과_공백은_건너뛴다() {
        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.LATE_NIGHT,
                MissionCategoryType.BASIC_ROUTINE,
                null,
                "   ",
                "친구에게 DM 보내기"
        )).isFalse();

        assertThat(MissionTimePolicy.isCandidateAllowed(
                MissionTimeSlot.LATE_NIGHT,
                null,
                (String[]) null
        )).isTrue();
    }

    @Test
    void 낮_시간대에는_차단_키워드가_없고_밤과_새벽은_서로_다르다() {
        assertThat(MissionTimePolicy.blockedMissionKeywords(MissionTimeSlot.MORNING)).isEmpty();
        assertThat(MissionTimePolicy.blockedMissionKeywords(MissionTimeSlot.AFTERNOON)).isEmpty();
        assertThat(MissionTimePolicy.blockedMissionKeywords(MissionTimeSlot.EVENING)).isEmpty();

        List<String> nightKeywords = MissionTimePolicy.blockedMissionKeywords(MissionTimeSlot.NIGHT);
        List<String> lateNightKeywords = MissionTimePolicy.blockedMissionKeywords(MissionTimeSlot.LATE_NIGHT);
        assertThat(nightKeywords).contains("햇빛", "sunlight");
        assertThat(nightKeywords).doesNotContain("전화", "점프");
        assertThat(lateNightKeywords).containsAll(nightKeywords).contains("전화", "점프");
    }

    @Test
    void 시간_분류는_LocalDateTime을_지원하고_잘못된_hour를_거부한다() {
        assertThat(MissionTimeSlot.from(LocalDateTime.of(2026, 6, 12, 20, 59)))
                .isEqualTo(MissionTimeSlot.EVENING);
        assertThat(MissionTimeSlot.from(LocalDateTime.of(2026, 6, 12, 21, 0)))
                .isEqualTo(MissionTimeSlot.NIGHT);

        assertThatThrownBy(() -> MissionTimeSlot.from(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MissionTimeSlot.fromHour(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MissionTimeSlot.fromHour(24))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MissionTemplate template(
            MissionCategoryType category,
            String title,
            String description,
            String characterMessage,
            String question,
            String completionResponse
    ) {
        MissionTemplate template = BeanUtils.instantiateClass(MissionTemplate.class);
        ReflectionTestUtils.setField(template, "category", category);
        ReflectionTestUtils.setField(template, "baseTitle", title);
        ReflectionTestUtils.setField(template, "baseDescription", description);
        ReflectionTestUtils.setField(template, "fallbackCharacterMessage", characterMessage);
        ReflectionTestUtils.setField(template, "fallbackQuestion", question);
        ReflectionTestUtils.setField(template, "fallbackCompletionResponse", completionResponse);
        return template;
    }
}
