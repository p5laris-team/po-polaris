package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.OnboardingDto;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OnboardingGatewayService {

    @GrpcClient("user")
    private OnboardingServiceGrpc.OnboardingServiceBlockingStub onboardingServiceStub;

    // 온보딩 질문 목록 조회
    public List<OnboardingDto.QuestionResponse> getQuestions() {
        GetQuestionsResponse response = onboardingServiceStub.getQuestions(GetQuestionsRequest.newBuilder().build());

        return response.getQuestionsList().stream()
                .map(q -> OnboardingDto.QuestionResponse.builder()
                        .key(q.getKey())
                        .content(q.getContent())
                        .multipleSelection(q.getMultipleSelection())
                        .maxSelectionCount(q.getMaxSelectionCount())
                        .options(q.getOptionsList().stream()
                                .map(opt -> OnboardingDto.AnswerOption.builder()
                                        .key(opt.getKey())
                                        .value(opt.getValue())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    // 내 온보딩 프로필 조회
    public OnboardingDto.ProfileResponse getProfile(Long userId) {
        GetProfileResponse response = onboardingServiceStub.getProfile(GetProfileRequest.newBuilder()
                .setUserId(userId)
                .build());

        return toDto(response.getProfile());
    }

    // 내 온보딩 프로필 저장
    public OnboardingDto.ProfileResponse saveProfile(Long userId, OnboardingDto.SaveProfileRequest request) {
        var profileBuilder = OnboardingProfileDto.newBuilder();
        
        if (request.getLivingType() != null) profileBuilder.setLivingType(request.getLivingType());
        if (request.getWakeUpTime() != null) profileBuilder.setWakeUpTime(request.getWakeUpTime());
        if (request.getSleepTime() != null) profileBuilder.setSleepTime(request.getSleepTime());
        if (request.getPreferredMissionTime() != null) profileBuilder.setPreferredMissionTime(request.getPreferredMissionTime());
        if (request.getRoutineGoal() != null) profileBuilder.setRoutineGoal(request.getRoutineGoal());
        if (request.getActivityPreference() != null) profileBuilder.setActivityPreference(request.getActivityPreference());
        if (request.getMissionIntensity() != null) profileBuilder.setMissionIntensity(request.getMissionIntensity());
        if (request.getAnswersJson() != null) profileBuilder.setAnswersJson(request.getAnswersJson());
        if (request.getCompleted() != null) profileBuilder.setCompleted(request.getCompleted());
        if (request.getOnboardingVersion() != null) profileBuilder.setOnboardingVersion(request.getOnboardingVersion());
        if (request.getRoutineGoals() != null) profileBuilder.addAllRoutineGoals(request.getRoutineGoals());
        if (request.getPreferredTimeSlots() != null) profileBuilder.addAllPreferredTimeSlots(request.getPreferredTimeSlots());
        if (request.getMissionPlaceContexts() != null) profileBuilder.addAllMissionPlaceContexts(request.getMissionPlaceContexts());
        if (request.getAvoidedMissionTags() != null) profileBuilder.addAllAvoidedMissionTags(request.getAvoidedMissionTags());

        SaveProfileResponse response = onboardingServiceStub.saveProfile(SaveProfileRequest.newBuilder()
                .setUserId(userId)
                .setProfile(profileBuilder.build())
                .build());

        return toDto(response.getProfile());
    }

    private OnboardingDto.ProfileResponse toDto(OnboardingProfileDto proto) {
        return OnboardingDto.ProfileResponse.builder()
                .livingType(proto.getLivingType().isEmpty() ? null : proto.getLivingType())
                .wakeUpTime(proto.getWakeUpTime().isEmpty() ? null : proto.getWakeUpTime())
                .sleepTime(proto.getSleepTime().isEmpty() ? null : proto.getSleepTime())
                .preferredMissionTime(proto.getPreferredMissionTime().isEmpty() ? null : proto.getPreferredMissionTime())
                .routineGoal(proto.getRoutineGoal().isEmpty() ? null : proto.getRoutineGoal())
                .activityPreference(proto.getActivityPreference().isEmpty() ? null : proto.getActivityPreference())
                .missionIntensity(proto.getMissionIntensity().isEmpty() ? null : proto.getMissionIntensity())
                .answersJson(proto.getAnswersJson().isEmpty() ? null : proto.getAnswersJson())
                .completed(proto.getCompleted())
                .onboardingVersion(proto.getOnboardingVersion())
                .routineGoals(proto.getRoutineGoalsList())
                .preferredTimeSlots(proto.getPreferredTimeSlotsList())
                .missionPlaceContexts(proto.getMissionPlaceContextsList())
                .avoidedMissionTags(proto.getAvoidedMissionTagsList())
                .build();
    }
}
