package p5laris.mission.domain.application.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionFeedback;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionFeedbackReaction;
import p5laris.mission.domain.domain.enums.MissionFeedbackReasonCode;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;
import p5laris.mission.domain.domain.repository.UserMemoryRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 미션 완료/피드백에서 RAG 원천 기억을 만든다.
 *
 * 이 서비스는 외부 API를 호출하지 않고 같은 mission DB에만 짧게 upsert한다.
 */
@Service
@RequiredArgsConstructor
public class MissionMemoryRecorder {

    private static final int COMPLETION_IMPORTANCE = 70;
    private static final int REJECTION_IMPORTANCE = 65;
    private static final int SATISFACTION_LIKE_IMPORTANCE = 45;
    private static final int SATISFACTION_DISLIKE_IMPORTANCE = 75;

    private final UserMemoryRepository userMemoryRepository;
    private final UserMemoryEmbeddingQueue userMemoryEmbeddingQueue;
    private final ObjectMapper objectMapper;

    public void recordCompletion(UserMission mission, MissionCompletionAnswer answer) {
        if (answer == null || answer.getId() == null || !answer.isAnswered()) {
            return;
        }

        upsert(
                mission.getUserId(),
                UserMemorySourceType.MISSION_COMPLETION_ANSWER,
                answer.getId(),
                UserMemoryType.MISSION_COMPLETION,
                "완료 답변: " + answer.getAnswerText(),
                missionMetadata(mission, Map.of(
                        "questionText", answer.getQuestionText()
                )),
                COMPLETION_IMPORTANCE
        );
    }

    public void recordRejection(UserMission mission, MissionFeedback feedback) {
        if (feedback == null || feedback.getId() == null || !hasMeaningfulRejectionSignal(feedback)) {
            return;
        }

        upsert(
                mission.getUserId(),
                UserMemorySourceType.MISSION_FEEDBACK,
                feedback.getId(),
                UserMemoryType.MISSION_REJECTION,
                rejectionContent(feedback),
                missionMetadata(mission, Map.of(
                        "reasonCode", feedback.getReasonCode().name(),
                        "hasReasonText", feedback.getReasonText() != null
                )),
                REJECTION_IMPORTANCE
        );
    }

    public void recordSatisfaction(UserMission mission, MissionFeedback feedback) {
        if (feedback == null || feedback.getId() == null || feedback.getReaction() == null) {
            return;
        }

        upsert(
                mission.getUserId(),
                UserMemorySourceType.MISSION_FEEDBACK,
                feedback.getId(),
                UserMemoryType.MISSION_SATISFACTION,
                satisfactionContent(feedback.getReaction()),
                missionMetadata(mission, Map.of(
                        "reaction", feedback.getReaction().name()
                )),
                satisfactionImportance(feedback.getReaction())
        );
    }

    private void upsert(
            Long userId,
            UserMemorySourceType sourceType,
            Long sourceId,
            UserMemoryType memoryType,
            String content,
            JsonNode metadataJson,
            int importance
    ) {
        UserMemory memory = userMemoryRepository
                .findBySourceTypeAndSourceIdAndMemoryType(sourceType, sourceId, memoryType)
                .orElseGet(() -> UserMemory.create(
                        userId,
                        sourceType,
                        sourceId,
                        memoryType,
                        content,
                        metadataJson,
                        importance
                ));

        if (memory.getId() != null
                && Objects.equals(memory.getContent(), content)
                && Objects.equals(memory.getMetadataJson(), metadataJson)
                && memory.getImportance() == importance) {
            return;
        }

        memory.update(content, metadataJson, importance);
        UserMemory savedMemory = userMemoryRepository.saveAndFlush(memory);
        userMemoryEmbeddingQueue.enqueue(savedMemory);
    }

    private boolean hasMeaningfulRejectionSignal(MissionFeedback feedback) {
        boolean hasReasonText = feedback.getReasonText() != null && !feedback.getReasonText().isBlank();
        boolean hasMeaningfulReasonCode = feedback.getReasonCode() != null
                && feedback.getReasonCode() != MissionFeedbackReasonCode.JUST_SKIP;
        return hasReasonText || hasMeaningfulReasonCode;
    }

    private String rejectionContent(MissionFeedback feedback) {
        String reasonCode = feedback.getReasonCode() == null
                ? MissionFeedbackReasonCode.JUST_SKIP.name()
                : feedback.getReasonCode().name();
        if (feedback.getReasonText() == null || feedback.getReasonText().isBlank()) {
            return "거절 이유 코드: " + reasonCode;
        }
        return "거절 이유: " + feedback.getReasonText() + " / 코드: " + reasonCode;
    }

    private String satisfactionContent(MissionFeedbackReaction reaction) {
        return switch (reaction) {
            case LIKE -> "만족도 피드백: 좋았어요";
            case DISLIKE -> "만족도 피드백: 별로였어요";
        };
    }

    private int satisfactionImportance(MissionFeedbackReaction reaction) {
        return switch (reaction) {
            case LIKE -> SATISFACTION_LIKE_IMPORTANCE;
            case DISLIKE -> SATISFACTION_DISLIKE_IMPORTANCE;
        };
    }

    private JsonNode missionMetadata(UserMission mission, Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("missionId", mission.getId());
        metadata.put("missionDate", mission.getMissionDate().toString());
        metadata.put("title", mission.getTitle());
        metadata.put("category", mission.getCategory().name());
        metadata.put("difficulty", mission.getDifficulty().name());
        metadata.put("status", mission.getStatus().name());
        metadata.put("aiGenerated", mission.getAiGenerationId() != null);
        metadata.putAll(extra);
        return objectMapper.valueToTree(metadata);
    }
}
