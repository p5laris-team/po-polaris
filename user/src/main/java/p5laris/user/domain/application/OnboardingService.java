package p5laris.user.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.user.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.OnboardingProfile;
import p5laris.user.domain.domain.repository.OnboardingProfileRepository;
import p5laris.user.domain.domain.enums.OnboardingQuestion;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingProfileRepository onboardingProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GetQuestionsResponse getQuestions() {
        var questions = Arrays.stream(OnboardingQuestion.values())
                .map(q -> QuestionDto.newBuilder()
                        .setKey(q.name())
                        .setContent(q.getContent())
                        .setMultipleSelection(q.isMultipleSelection())
                        .setMaxSelectionCount(q.getMaxSelectionCount())
                        .addAllOptions(q.getOptions().stream()
                                .map(opt -> AnswerOptionDto.newBuilder()
                                        .setKey(opt.key())
                                        .setValue(opt.value())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return GetQuestionsResponse.newBuilder()
                .addAllQuestions(questions)
                .build();
    }

    @Transactional(readOnly = true)
    public GetProfileResponse getProfile(Long userId) {
        OnboardingProfile profile = onboardingProfileRepository.findByUserId(userId)
                .orElseGet(() -> OnboardingProfile.builder().userId(userId).build());

        return GetProfileResponse.newBuilder()
                .setProfile(toDto(profile))
                .build();
    }

    @Transactional
    public SaveProfileResponse saveProfile(Long userId, OnboardingProfileDto dto) {
        OnboardingProfile profile = onboardingProfileRepository.findByUserId(userId)
                .orElseGet(() -> OnboardingProfile.builder().userId(userId).build());
        boolean wasCompleted = profile.isCompleted();
        OnboardingProfileV2 v2Profile = toV2Profile(profile, dto);

        profile.updateProfile(
                dto.getLivingType(),
                dto.getWakeUpTime(),
                dto.getSleepTime(),
                firstOrFallback(v2Profile.preferredTimeSlots(), dto.getPreferredMissionTime()),
                firstOrFallback(v2Profile.routineGoals(), dto.getRoutineGoal()),
                dto.getMissionIntensity(),
                firstOrFallback(v2Profile.missionPlaceContexts(), dto.getActivityPreference()),
                normalizeAnswersJson(dto.getAnswersJson()),
                dto.getCompleted(),
                v2Profile.onboardingVersion(),
                toJsonArray(v2Profile.routineGoals()),
                toJsonArray(v2Profile.preferredTimeSlots()),
                toJsonArray(v2Profile.missionPlaceContexts()),
                toJsonArray(v2Profile.avoidedMissionTags())
        );
        
        OnboardingProfile savedProfile = onboardingProfileRepository.save(profile);

        eventPublisher.publishEvent(UserEventLogEvent.onboardingProfileSaved(savedProfile));
        if (!wasCompleted && savedProfile.isCompleted()) {
            eventPublisher.publishEvent(UserEventLogEvent.onboardingCompleted(savedProfile));
        }

        return SaveProfileResponse.newBuilder()
                .setProfile(toDto(savedProfile))
                .build();
    }

    private OnboardingProfileDto toDto(OnboardingProfile profile) {
        var builder = OnboardingProfileDto.newBuilder();
        if (profile.getLivingType() != null) builder.setLivingType(profile.getLivingType());
        if (profile.getWakeUpTime() != null) builder.setWakeUpTime(profile.getWakeUpTime());
        if (profile.getSleepTime() != null) builder.setSleepTime(profile.getSleepTime());
        if (profile.getPreferredMissionTime() != null) builder.setPreferredMissionTime(profile.getPreferredMissionTime());
        if (profile.getRoutineGoal() != null) builder.setRoutineGoal(profile.getRoutineGoal());
        if (profile.getActivityPreference() != null) builder.setActivityPreference(profile.getActivityPreference());
        if (profile.getMissionIntensity() != null) builder.setMissionIntensity(profile.getMissionIntensity());
        if (profile.getAnswersJson() != null) builder.setAnswersJson(profile.getAnswersJson());
        builder.setCompleted(profile.isCompleted());
        builder.setOnboardingVersion(profile.getOnboardingVersion());
        builder.addAllRoutineGoals(fromJsonArray(profile.getRoutineGoalsJson()));
        builder.addAllPreferredTimeSlots(fromJsonArray(profile.getPreferredTimeSlotsJson()));
        builder.addAllMissionPlaceContexts(fromJsonArray(profile.getMissionPlaceContextsJson()));
        builder.addAllAvoidedMissionTags(fromJsonArray(profile.getAvoidedMissionTagsJson()));
        return builder.build();
    }

    private OnboardingProfileV2 toV2Profile(OnboardingProfile currentProfile, OnboardingProfileDto dto) {
        List<String> routineGoals = normalizeAnswers(
                dto.getRoutineGoalsList().isEmpty() ? List.of(dto.getRoutineGoal()) : dto.getRoutineGoalsList(),
                OnboardingQuestion.ROUTINE_GOAL.getMaxSelectionCount()
        );
        List<String> preferredTimeSlots = normalizeAnswers(
                dto.getPreferredTimeSlotsList().isEmpty()
                        ? List.of(dto.getPreferredMissionTime())
                        : dto.getPreferredTimeSlotsList(),
                OnboardingQuestion.PREFERRED_MISSION_TIME.getMaxSelectionCount()
        );
        List<String> missionPlaceContexts = normalizeAnswers(
                dto.getMissionPlaceContextsList().isEmpty()
                        ? List.of(dto.getActivityPreference())
                        : dto.getMissionPlaceContextsList(),
                OnboardingQuestion.MISSION_PLACE_CONTEXT.getMaxSelectionCount()
        );
        List<String> avoidedMissionTags = normalizeAnswers(
                dto.getAvoidedMissionTagsList(),
                OnboardingQuestion.AVOIDED_MISSION_TAGS.getMaxSelectionCount()
        );

        boolean hasV2Answers = !routineGoals.isEmpty()
                || !preferredTimeSlots.isEmpty()
                || !missionPlaceContexts.isEmpty()
                || !avoidedMissionTags.isEmpty();
        int requestedVersion = dto.getOnboardingVersion();
        int version = requestedVersion > 0
                ? requestedVersion
                : hasV2Answers ? 2 : currentProfile.getOnboardingVersion();

        return new OnboardingProfileV2(
                version,
                routineGoals,
                preferredTimeSlots,
                missionPlaceContexts,
                avoidedMissionTags
        );
    }

    private List<String> normalizeAnswers(List<String> values, int maxCount) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(maxCount)
                .toList();
    }

    private String firstOrFallback(List<String> values, String fallback) {
        if (!values.isEmpty()) {
            return values.get(0);
        }

        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private String toJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String normalizeAnswersJson(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return null;
        }

        return answersJson.trim();
    }

    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private record OnboardingProfileV2(
            int onboardingVersion,
            List<String> routineGoals,
            List<String> preferredTimeSlots,
            List<String> missionPlaceContexts,
            List<String> avoidedMissionTags
    ) {
    }
}
