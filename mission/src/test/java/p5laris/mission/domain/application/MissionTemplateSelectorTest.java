package p5laris.mission.domain.application;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.mission.domain.application.time.MissionTimeSlot;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTemplateSelectorTest {

    private final MissionTemplateSelector selector = new MissionTemplateSelector();

    @Test
    void 같은_유저와_같은_날짜는_항상_같은_템플릿을_선택한다() {
        List<MissionTemplate> templates = templates(1, 2, 3, 4, 5, 6, 7);
        LocalDate missionDate = LocalDate.of(2026, 5, 25);

        MissionTemplate first = selector.selectNextTemplate(1001L, missionDate, templates, Set.of());
        MissionTemplate second = selector.selectNextTemplate(1001L, missionDate, templates, Set.of());

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void 오늘_이미_사용한_템플릿은_다음_후보에서_제외한다() {
        List<MissionTemplate> templates = templates(1, 2, 3, 4, 5, 6, 7);
        LocalDate missionDate = LocalDate.of(2026, 5, 25);
        MissionTemplate first = selector.selectNextTemplate(1001L, missionDate, templates, Set.of());

        MissionTemplate next = selector.selectNextTemplate(
                1001L,
                missionDate,
                templates,
                Set.of(first.getId())
        );

        assertThat(next.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void 날짜가_바뀌면_템플릿_선택_순서도_달라질_수_있다() {
        List<MissionTemplate> templates = templates(1, 2, 3, 4, 5, 6, 7);
        LocalDate startDate = LocalDate.of(2026, 5, 25);

        Set<Long> selectedTemplateIds = IntStream.range(0, 14)
                .mapToObj(startDate::plusDays)
                .map(date -> selector.selectNextTemplate(1001L, date, templates, Set.of()).getId())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(selectedTemplateIds).hasSizeGreaterThan(1);
    }

    @Test
    void 모든_활성_템플릿을_오늘_이미_사용했으면_예외를_던진다() {
        List<MissionTemplate> templates = templates(1, 2, 3);

        assertThatThrownBy(() -> selector.selectNextTemplate(
                1001L,
                LocalDate.of(2026, 5, 25),
                templates,
                Set.of(1L, 2L, 3L)
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_TEMPLATE_NOT_FOUND);
    }

    @Test
    void 밤에는_OUTDOOR_LIGHT_템플릿을_선택하지_않는다() {
        List<MissionTemplate> templates = List.of(
                template(1, MissionCategoryType.OUTDOOR_LIGHT, "짧은 햇빛 충전하기", "햇빛에 잠깐 머물러보세요."),
                template(2, MissionCategoryType.REST_RECOVERY, "잠들기 전 호흡 고르기", "자리에서 천천히 숨을 골라보세요.")
        );

        MissionTemplate selected = selector.selectNextTemplate(
                1001L,
                LocalDate.of(2026, 5, 25),
                templates,
                Set.of(),
                MissionTimeSlot.NIGHT
        );

        assertThat(selected.getCategory()).isEqualTo(MissionCategoryType.REST_RECOVERY);
    }

    @Test
    void 새벽에는_SOCIAL_LIGHT와_OUTDOOR_LIGHT_템플릿을_선택하지_않는다() {
        List<MissionTemplate> templates = List.of(
                template(1, MissionCategoryType.OUTDOOR_LIGHT, "햇빛 있는 곳 보기", "햇빛이나 밝은 곳을 잠깐 바라보세요."),
                template(2, MissionCategoryType.SOCIAL_LIGHT, "안부 메시지 보내기", "생각나는 사람에게 짧게 메시지를 보내보세요."),
                template(3, MissionCategoryType.MIND_RECORD, "오늘 마음 한 줄 쓰기", "지금 마음을 한 줄로 적어보세요.")
        );

        MissionTemplate selected = selector.selectNextTemplate(
                1001L,
                LocalDate.of(2026, 5, 25),
                templates,
                Set.of(),
                MissionTimeSlot.LATE_NIGHT
        );

        assertThat(selected.getCategory()).isEqualTo(MissionCategoryType.MIND_RECORD);
    }

    private List<MissionTemplate> templates(long... ids) {
        return java.util.Arrays.stream(ids)
                .mapToObj(this::template)
                .toList();
    }

    private MissionTemplate template(long id) {
        try {
            Constructor<MissionTemplate> constructor = MissionTemplate.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MissionTemplate template = constructor.newInstance();
            ReflectionTestUtils.setField(template, "id", id);
            return template;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MissionTemplate test fixture creation failed.", e);
        }
    }

    private MissionTemplate template(
            long id,
            MissionCategoryType category,
            String baseTitle,
            String baseDescription
    ) {
        MissionTemplate template = template(id);
        ReflectionTestUtils.setField(template, "category", category);
        ReflectionTestUtils.setField(template, "baseTitle", baseTitle);
        ReflectionTestUtils.setField(template, "baseDescription", baseDescription);
        ReflectionTestUtils.setField(template, "fallbackCharacterMessage", baseTitle);
        ReflectionTestUtils.setField(template, "fallbackQuestion", "어땠나요?");
        ReflectionTestUtils.setField(template, "fallbackCompletionResponse", "좋아요.");
        return template;
    }
}
