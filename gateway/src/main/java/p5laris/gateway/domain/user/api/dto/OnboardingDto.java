package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

public class OnboardingDto {

    @Getter
    @Builder
    public static class QuestionResponse {
        private String key;
        private String content;
        private List<AnswerOption> options;
        private boolean multipleSelection;
        private int maxSelectionCount;
    }

    @Getter
    @Builder
    public static class AnswerOption {
        private String key;
        private String value;
    }

    @Getter
    @Builder
    public static class ProfileResponse {
        private String livingType;
        private String wakeUpTime;
        private String sleepTime;
        private String preferredMissionTime;
        private String routineGoal;
        private String activityPreference;
        private String missionIntensity;
        private String answersJson;
        private boolean completed;
        private int onboardingVersion;
        private List<String> routineGoals;
        private List<String> preferredTimeSlots;
        private List<String> missionPlaceContexts;
        private List<String> avoidedMissionTags;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveProfileRequest {
        private String livingType;
        private String wakeUpTime;
        private String sleepTime;
        private String preferredMissionTime;
        private String routineGoal;
        private String activityPreference;
        private String missionIntensity;
        private String answersJson;
        private Boolean completed;
        private Integer onboardingVersion;
        private List<String> routineGoals;
        private List<String> preferredTimeSlots;
        private List<String> missionPlaceContexts;
        private List<String> avoidedMissionTags;
    }
}
