package p5laris.mission.domain.application.event;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MissionEventLogEventTest {

    @Test
    void 미션_완료_이벤트에는_답변_전문을_담지_않고_요약값만_담는다() throws Exception {
        UserMission mission = mission();
        MissionCompletionAnswer answer = answer("책상 위 컵을 싱크대로 옮겼어");
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 5, 20, 10, 8, 30, 0, ZoneOffset.ofHours(9));

        MissionEventLogEvent event = MissionEventLogEvent.missionCompleted(mission, answer, occurredAt);

        assertThat(event.eventType()).isEqualTo("MISSION_COMPLETED");
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.refType()).isEqualTo("MISSION");
        assertThat(event.refId()).isEqualTo(101L);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);

        Map<String, Object> metadata = event.metadata();
        assertThat(metadata)
                .containsEntry("characterId", 3L)
                .containsEntry("missionTemplateId", 12L)
                .containsEntry("aiGenerationId", 55L)
                .containsEntry("missionDate", "2026-05-20")
                .containsEntry("stackOrder", 2)
                .containsEntry("category", MissionCategoryType.SPACE_RESET)
                .containsEntry("difficulty", MissionDifficultyType.EASY)
                .containsEntry("rewardStarPiece", 10)
                .containsEntry("answerLength", "책상 위 컵을 싱크대로 옮겼어".length())
                .containsEntry("elapsedSecondsSinceOffered", 510L)
                .containsEntry("elapsedSecondsSinceCompletionStarted", 90L);
        assertThat(metadata).doesNotContainKeys("answerText", "questionText");
    }

    @Test
    void 완료_질문_시작_이벤트는_질문_id와_입력_정책만_담는다() throws Exception {
        UserMission mission = mission();
        MissionCompletionAnswer answer = answer(null);
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 5, 20, 10, 7, 0, 0, ZoneOffset.ofHours(9));

        MissionEventLogEvent event = MissionEventLogEvent.completionSessionStarted(mission, answer, occurredAt);

        assertThat(event.eventType()).isEqualTo("MISSION_COMPLETION_SESSION_STARTED");
        assertThat(event.metadata())
                .containsEntry("questionId", 501L)
                .containsEntry("inputType", "TEXT")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 300)
                .containsEntry("elapsedSecondsSinceOffered", 420L);
        assertThat(event.metadata()).doesNotContainKeys("answerText", "questionText");
    }

    private UserMission mission() throws Exception {
        UserMission mission = newInstance(UserMission.class);
        ReflectionTestUtils.setField(mission, "id", 101L);
        ReflectionTestUtils.setField(mission, "userId", 1L);
        ReflectionTestUtils.setField(mission, "characterId", 3L);
        ReflectionTestUtils.setField(mission, "missionTemplateId", 12L);
        ReflectionTestUtils.setField(mission, "aiGenerationId", 55L);
        ReflectionTestUtils.setField(mission, "missionDate", LocalDate.of(2026, 5, 20));
        ReflectionTestUtils.setField(mission, "stackOrder", 2);
        ReflectionTestUtils.setField(mission, "category", MissionCategoryType.SPACE_RESET);
        ReflectionTestUtils.setField(mission, "difficulty", MissionDifficultyType.EASY);
        ReflectionTestUtils.setField(mission, "rewardStarPiece", 10);
        ReflectionTestUtils.setField(mission, "offeredAt", LocalDateTime.of(2026, 5, 20, 10, 0, 0));
        ReflectionTestUtils.setField(mission, "completionStartedAt", LocalDateTime.of(2026, 5, 20, 10, 7, 0));
        return mission;
    }

    private MissionCompletionAnswer answer(String answerText) throws Exception {
        MissionCompletionAnswer answer = newInstance(MissionCompletionAnswer.class);
        ReflectionTestUtils.setField(answer, "id", 501L);
        ReflectionTestUtils.setField(answer, "missionId", 101L);
        ReflectionTestUtils.setField(answer, "userId", 1L);
        ReflectionTestUtils.setField(answer, "questionText", "어떤 물건을 치웠어?");
        ReflectionTestUtils.setField(answer, "answerText", answerText);
        return answer;
    }

    private <T> T newInstance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
