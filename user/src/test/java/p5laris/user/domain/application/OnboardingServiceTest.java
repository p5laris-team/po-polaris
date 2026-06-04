package p5laris.user.domain.application;

import com.p5laris.proto.user.v1.OnboardingProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.repository.OnboardingProfileRepository;
import p5laris.user.domain.domain.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "grpc.client.character.address=static://localhost:19091",
        "grpc.client.item.address=static://localhost:19092",
        "grpc.client.mission.address=static://localhost:19093",
        "grpc.client.ai.address=static://localhost:19094",
        "grpc.client.event-log.address=static://localhost:19095",
        "grpc.client.notification.address=static://localhost:19096",
        "internal.grpc-auth.enabled=true",
        "internal.grpc-auth.token=test-internal-grpc-token"
})
class OnboardingServiceTest {

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnboardingProfileRepository onboardingProfileRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("onboarding-test@p5laris.life")
                .ifPresent(existingUser -> {
                    onboardingProfileRepository.findByUserId(existingUser.getId())
                            .ifPresent(onboardingProfileRepository::delete);
                    userRepository.delete(existingUser);
                });
        User user = userRepository.save(User.builder()
                .email("onboarding-test@p5laris.life")
                .nickname("온보딩테스터")
                .provider("GOOGLE")
                .role("USER")
                .status("ACTIVE")
                .build());
        userId = user.getId();
    }

    @Test
    void 온보딩_질문은_개인화_질문_5개만_반환한다() {
        var response = onboardingService.getQuestions();

        assertThat(response.getQuestionsList())
                .extracting("key")
                .containsExactly(
                        "ROUTINE_GOAL",
                        "PREFERRED_MISSION_TIME",
                        "MISSION_PLACE_CONTEXT",
                        "MISSION_INTENSITY",
                        "AVOIDED_MISSION_TAGS"
                );
        assertThat(response.getQuestions(0).getMultipleSelection()).isTrue();
        assertThat(response.getQuestions(0).getMaxSelectionCount()).isEqualTo(3);
        assertThat(response.getQuestions(0).getOptionsList())
                .extracting("key")
                .contains("EXERCISE_HABIT");
    }

    @Test
    void 온보딩_복수_선택값을_저장하고_조회한다() {
        OnboardingProfileDto request = OnboardingProfileDto.newBuilder()
                .addAllRoutineGoals(List.of(
                        "HYDRATION_MEAL",
                        "EXERCISE_HABIT",
                        "REST_RECOVERY",
                        "SPACE_RESET"
                ))
                .addAllPreferredTimeSlots(List.of("MORNING", "NIGHT"))
                .addAllMissionPlaceContexts(List.of("HOME", "WORK_SCHOOL"))
                .addAllAvoidedMissionTags(List.of("SOCIAL_CONTACT"))
                .setMissionIntensity("VERY_LIGHT")
                .setCompleted(true)
                .build();

        onboardingService.saveProfile(userId, request);
        var response = onboardingService.getProfile(userId).getProfile();

        assertThat(response.getOnboardingVersion()).isEqualTo(2);
        assertThat(response.getRoutineGoalsList())
                .containsExactly("HYDRATION_MEAL", "EXERCISE_HABIT", "REST_RECOVERY");
        assertThat(response.getRoutineGoal()).isEqualTo("HYDRATION_MEAL");
        assertThat(response.getPreferredTimeSlotsList()).containsExactly("MORNING", "NIGHT");
        assertThat(response.getMissionPlaceContextsList()).containsExactly("HOME", "WORK_SCHOOL");
        assertThat(response.getAvoidedMissionTagsList()).containsExactly("SOCIAL_CONTACT");
        assertThat(response.getCompleted()).isTrue();
    }
}
