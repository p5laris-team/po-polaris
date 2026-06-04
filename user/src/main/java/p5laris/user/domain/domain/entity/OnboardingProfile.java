package p5laris.user.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import p5laris.user.core.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_profiles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OnboardingProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    private String livingType;
    private String wakeUpTime;
    private String sleepTime;
    private String preferredMissionTime;
    private String routineGoal;
    private String activityPreference;
    private String missionIntensity;

    @Builder.Default
    private int onboardingVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "routine_goals_json", columnDefinition = "jsonb")
    @Builder.Default
    private String routineGoalsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_time_slots_json", columnDefinition = "jsonb")
    @Builder.Default
    private String preferredTimeSlotsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mission_place_contexts_json", columnDefinition = "jsonb")
    @Builder.Default
    private String missionPlaceContextsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoided_mission_tags_json", columnDefinition = "jsonb")
    @Builder.Default
    private String avoidedMissionTagsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String answersJson;

    @Builder.Default
    private boolean completed = false;

    private LocalDateTime completedAt;

    public void updateProfile(String livingType, String wakeUpTime, String sleepTime, 
                              String preferredMissionTime, String routineGoal, 
                              String missionIntensity, String activityPreference,
                              String answersJson, boolean completed,
                              int onboardingVersion,
                              String routineGoalsJson,
                              String preferredTimeSlotsJson,
                              String missionPlaceContextsJson,
                              String avoidedMissionTagsJson) {
        this.livingType = livingType;
        this.wakeUpTime = wakeUpTime;
        this.sleepTime = sleepTime;
        this.preferredMissionTime = preferredMissionTime;
        this.routineGoal = routineGoal;
        this.missionIntensity = missionIntensity;
        this.activityPreference = activityPreference;
        this.onboardingVersion = onboardingVersion;
        this.routineGoalsJson = routineGoalsJson;
        this.preferredTimeSlotsJson = preferredTimeSlotsJson;
        this.missionPlaceContextsJson = missionPlaceContextsJson;
        this.avoidedMissionTagsJson = avoidedMissionTagsJson;
        this.answersJson = answersJson;
        this.completed = completed;
        if (completed && this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }
}
