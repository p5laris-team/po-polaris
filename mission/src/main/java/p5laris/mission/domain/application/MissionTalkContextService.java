package p5laris.mission.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.mission.v1.GetMissionTalkContextResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p5laris.mission.domain.application.personalization.MissionPersonalizationContext;
import p5laris.mission.domain.application.personalization.MissionPersonalizationContextBuilder;
import p5laris.mission.domain.application.weather.MissionWeatherContextService;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 별친구 대화 Tool Calling에서 사용할 미션 context를 조립한다.
 *
 * 화면용 미션 조회 응답을 그대로 넘기지 않고, 대화에 필요한 오늘 미션/최근 루틴/환경 정보만
 * 작은 JSON 문자열로 내려 AI prompt 토큰과 모듈 간 응답 크기를 제한한다.
 */
@Service
@RequiredArgsConstructor
public class MissionTalkContextService {

    private static final int ANSWER_PREVIEW_MAX_LENGTH = 60;
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserMissionRepository userMissionRepository;
    private final MissionCompletionAnswerRepository missionCompletionAnswerRepository;
    private final MissionPersonalizationContextBuilder missionPersonalizationContextBuilder;
    private final MissionWeatherContextService missionWeatherContextService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GetMissionTalkContextResponse getMissionTalkContext(Long userId) {
        LocalDate today = LocalDate.now(clock);
        String todayMissionContextJson = toJson(todayMissionContext(userId, today));

        MissionPersonalizationContext personalizationContext = missionPersonalizationContextBuilder.build(userId, today);
        String recentRoutineContextJson = missionWeatherContextService.enrich(
                userId,
                personalizationContext.recentMissionContextJson()
        );

        return GetMissionTalkContextResponse.newBuilder()
                .setTodayMissionContextJson(todayMissionContextJson)
                .setRecentRoutineContextJson(recentRoutineContextJson)
                .setEnvironmentContextJson(extractEnvironmentContext(recentRoutineContextJson))
                .build();
    }

    private Map<String, Object> todayMissionContext(Long userId, LocalDate today) {
        List<UserMission> missions = userMissionRepository.findByUserIdAndMissionDateOrderByStackOrderAsc(userId, today);
        Map<Long, MissionCompletionAnswer> answerByMissionId = findAnswersByMissionId(missions);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", today.toString());
        context.put("offeredCount", missions.size());
        context.put("completedCount", countByStatus(missions, UserMissionStatus.COMPLETED));
        context.put("rejectedCount", countByStatus(missions, UserMissionStatus.REJECTED));
        context.put("activeMissionId", activeMissionId(missions));
        context.put("missions", missions.stream()
                .map(mission -> todayMissionItem(mission, answerByMissionId.get(mission.getId())))
                .toList());
        return context;
    }

    private Map<String, Object> todayMissionItem(UserMission mission, MissionCompletionAnswer answer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("missionId", mission.getId());
        item.put("title", mission.getTitle());
        item.put("category", mission.getCategory().name());
        item.put("difficulty", mission.getDifficulty().name());
        item.put("status", mission.getStatus().name());
        item.put("rewardStarPiece", mission.getRewardStarPiece());
        item.put("createdAt", formatDateTime(mission.getCreatedAt()));
        item.put("completedAt", formatDateTime(mission.getCompletedAt()));
        item.put("rejectedAt", formatDateTime(mission.getRejectedAt()));

        if (answer != null) {
            item.put("completionQuestion", answer.getQuestionText());
            item.put("hasAnswer", answer.isAnswered());
            if (answer.isAnswered()) {
                item.put("answerPreview", truncate(answer.getAnswerText(), ANSWER_PREVIEW_MAX_LENGTH));
            }
        } else {
            item.put("hasAnswer", false);
        }
        return item;
    }

    private Map<Long, MissionCompletionAnswer> findAnswersByMissionId(List<UserMission> missions) {
        if (missions.isEmpty()) {
            return Map.of();
        }

        List<Long> missionIds = missions.stream()
                .map(UserMission::getId)
                .toList();
        return missionCompletionAnswerRepository.findByMissionIdIn(missionIds).stream()
                .collect(Collectors.toMap(MissionCompletionAnswer::getMissionId, Function.identity()));
    }

    private int countByStatus(List<UserMission> missions, UserMissionStatus status) {
        return (int) missions.stream()
                .filter(mission -> mission.getStatus() == status)
                .count();
    }

    private Long activeMissionId(List<UserMission> missions) {
        return missions.stream()
                .filter(mission -> mission.getStatus() == UserMissionStatus.OFFERED
                        || mission.getStatus() == UserMissionStatus.ANSWERING)
                .map(UserMission::getId)
                .findFirst()
                .orElse(0L);
    }

    private String extractEnvironmentContext(String recentRoutineContextJson) {
        try {
            JsonNode root = objectMapper.readTree(recentRoutineContextJson);
            JsonNode environmentContext = root.get("environmentContext");
            if (environmentContext == null || environmentContext.isNull()) {
                return EMPTY_JSON_OBJECT;
            }
            return objectMapper.writeValueAsString(environmentContext);
        } catch (Exception e) {
            return EMPTY_JSON_OBJECT;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return EMPTY_JSON_OBJECT;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }
}
