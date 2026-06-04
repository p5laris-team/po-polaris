package p5laris.mission.domain.application.time;

import org.junit.jupiter.api.Test;
import p5laris.mission.domain.domain.enums.MissionCategoryType;

import static org.assertj.core.api.Assertions.assertThat;

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
}
