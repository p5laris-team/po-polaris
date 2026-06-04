package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetWeatherRegionRequest;
import com.p5laris.proto.user.v1.GetWeatherRegionResponse;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import com.p5laris.proto.user.v1.WeatherRegion;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * mission은 user DB를 직접 보지 않고 gRPC로 사용자가 선택한 날씨 권역만 조회한다.
 */
@Slf4j
@Component
public class UserWeatherRegionClient {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public Optional<UserWeatherRegionSnapshot> findWeatherRegion(Long userId) {
        try {
            GetWeatherRegionResponse response = userStub.getWeatherRegion(
                    GetWeatherRegionRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            if (!response.getSelected() || !response.hasRegion()) {
                return Optional.empty();
            }

            WeatherRegion region = response.getRegion();
            if (region.getNx() <= 0 || region.getNy() <= 0 || region.getDisplayName().isBlank()) {
                log.warn("사용자 날씨 권역 응답이 올바르지 않습니다. userId={}, regionCode={}", userId, region.getCode());
                return Optional.empty();
            }

            return Optional.of(new UserWeatherRegionSnapshot(
                    region.getCode(),
                    region.getDisplayName(),
                    region.getNx(),
                    region.getNy()
            ));
        } catch (StatusRuntimeException e) {
            log.warn("사용자 날씨 권역 조회에 실패했습니다. userId={}, status={}", userId, e.getStatus().getCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("사용자 날씨 권역 조회에 실패했습니다. userId={}", userId, e);
            return Optional.empty();
        }
    }

    public record UserWeatherRegionSnapshot(
            String regionCode,
            String displayName,
            int nx,
            int ny
    ) {
    }
}
