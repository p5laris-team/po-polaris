package p5laris.mission.domain.application.event;

import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.UserMission;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * mission 모듈에서 event-log 모듈로 보낼 비즈니스 이벤트 스냅샷이다.
 *
 * 이 record에는 gRPC 호출에 필요한 값만 담는다.
 * 사용자 완료 답변 전문은 민감도가 있으므로 넣지 않고, 길이와 소요 시간 같은 분석용 요약값만 담는다.
 */
public record MissionEventLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt
) {

    public static MissionEventLogEvent missionOffered(UserMission mission, OffsetDateTime occurredAt) {
        Map<String, Object> metadata = baseMissionMetadata(mission);
        metadata.put("rewardStarPiece", mission.getRewardStarPiece());

        return new MissionEventLogEvent(
                "MISSION_OFFERED",
                mission.getUserId(),
                "MISSION",
                mission.getId(),
                metadata,
                occurredAt
        );
    }

    public static MissionEventLogEvent missionRejected(UserMission mission, OffsetDateTime occurredAt) {
        Map<String, Object> metadata = baseMissionMetadata(mission);
        putElapsedSeconds(metadata, "elapsedSecondsSinceOffered", mission.getOfferedAt(), occurredAt);

        return new MissionEventLogEvent(
                "MISSION_REJECTED",
                mission.getUserId(),
                "MISSION",
                mission.getId(),
                metadata,
                occurredAt
        );
    }

    public static MissionEventLogEvent completionSessionStarted(
            UserMission mission,
            MissionCompletionAnswer answer,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> metadata = baseMissionMetadata(mission);
        metadata.put("questionId", answer.getId());
        metadata.put("inputType", "TEXT");
        metadata.put("minLength", 1);
        metadata.put("maxLength", 300);
        putElapsedSeconds(metadata, "elapsedSecondsSinceOffered", mission.getOfferedAt(), occurredAt);

        return new MissionEventLogEvent(
                "MISSION_COMPLETION_SESSION_STARTED",
                mission.getUserId(),
                "MISSION",
                mission.getId(),
                metadata,
                occurredAt
        );
    }

    public static MissionEventLogEvent missionCompleted(
            UserMission mission,
            MissionCompletionAnswer answer,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> metadata = baseMissionMetadata(mission);
        metadata.put("rewardStarPiece", mission.getRewardStarPiece());
        metadata.put("answerLength", answer.getAnswerText() == null ? 0 : answer.getAnswerText().length());
        putElapsedSeconds(metadata, "elapsedSecondsSinceOffered", mission.getOfferedAt(), occurredAt);
        putElapsedSeconds(
                metadata,
                "elapsedSecondsSinceCompletionStarted",
                mission.getCompletionStartedAt(),
                occurredAt
        );

        return new MissionEventLogEvent(
                "MISSION_COMPLETED",
                mission.getUserId(),
                "MISSION",
                mission.getId(),
                metadata,
                occurredAt
        );
    }

    private static Map<String, Object> baseMissionMetadata(UserMission mission) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("characterId", mission.getCharacterId());
        metadata.put("missionTemplateId", mission.getMissionTemplateId());
        metadata.put("aiGenerationId", mission.getAiGenerationId());
        metadata.put("missionDate", mission.getMissionDate().toString());
        metadata.put("stackOrder", mission.getStackOrder());
        if (mission.getTitle() != null && !mission.getTitle().isBlank()) {
            metadata.put("missionTitle", mission.getTitle());
        }
        metadata.put("category", mission.getCategory());
        metadata.put("difficulty", mission.getDifficulty());
        return metadata;
    }

    private static void putElapsedSeconds(
            Map<String, Object> metadata,
            String key,
            LocalDateTime startedAt,
            OffsetDateTime occurredAt
    ) {
        if (startedAt == null) {
            return;
        }

        long elapsedSeconds = Duration.between(startedAt, occurredAt.toLocalDateTime()).getSeconds();
        metadata.put(key, Math.max(0, elapsedSeconds));
    }
}
