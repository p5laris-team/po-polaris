package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetProfileRequest;
import com.p5laris.proto.user.v1.OnboardingProfileDto;
import com.p5laris.proto.user.v1.OnboardingServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * mission 모듈에서 user 모듈의 온보딩 프로필을 읽어오는 adapter다.
 *
 * 온보딩은 개인화 품질을 높이는 입력값이지만, 조회 실패가 미션 생성 실패로 이어지면 안 된다.
 * 그래서 gRPC 실패 시 Optional.empty()를 반환하고 mission은 히스토리/기본 정책만으로 생성을 이어간다.
 */
@Slf4j
@Component
public class OnboardingProfileClient {

    @GrpcClient("user")
    private OnboardingServiceGrpc.OnboardingServiceBlockingStub onboardingStub;

    public Optional<OnboardingProfileSnapshot> findProfile(Long userId) {
        try {
            OnboardingProfileDto profile = onboardingStub.getProfile(GetProfileRequest.newBuilder()
                            .setUserId(userId)
                            .build())
                    .getProfile();

            return Optional.of(OnboardingProfileSnapshot.from(profile));
        } catch (StatusRuntimeException e) {
            log.warn("온보딩 프로필 조회 실패. userId={}, status={}", userId, e.getStatus().getCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("온보딩 프로필 조회 실패. userId={}", userId, e);
            return Optional.empty();
        }
    }

    public record OnboardingProfileSnapshot(
            int onboardingVersion,
            List<String> routineGoals,
            List<String> preferredTimeSlots,
            List<String> missionPlaceContexts,
            String missionIntensity,
            List<String> avoidedMissionTags,
            boolean completed
    ) {

        private static OnboardingProfileSnapshot from(OnboardingProfileDto profile) {
            return new OnboardingProfileSnapshot(
                    profile.getOnboardingVersion(),
                    profile.getRoutineGoalsList(),
                    profile.getPreferredTimeSlotsList(),
                    profile.getMissionPlaceContextsList(),
                    profile.getMissionIntensity(),
                    profile.getAvoidedMissionTagsList(),
                    profile.getCompleted()
            );
        }
    }
}
