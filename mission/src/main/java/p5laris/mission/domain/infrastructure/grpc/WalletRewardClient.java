package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.config.MissionRewardWalletProperties;

import java.util.concurrent.TimeUnit;

/**
 * mission 모듈에서 user/wallet 모듈의 별조각 잔액을 조회하는 gRPC adapter다.
 *
 * 별조각 지급 write path는 Kafka 적립 요청/결과 이벤트가 담당한다.
 * 이 클래스는 이미 지급이 끝난 응답을 다시 만들 때 현재 잔액을 조회하는 read path만 맡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRewardClient {

    private final MissionRewardWalletProperties properties;

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    /**
     * 이미 보상 지급이 끝난 미션 응답을 다시 만들 때 현재 wallet 잔액만 조회한다.
     *
     * 이 조회는 새 거래를 만들지 않으므로 중복 요청 응답을 만들 때 사용한다.
     */
    public int getWalletStarPiece(Long userId) {
        try {
            WalletResponse response = deadlineWalletStub().getMyWallet(
                    GetMyWalletRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );
            return response.getStarPiece();
        } catch (StatusRuntimeException e) {
            log.warn("wallet 잔액 조회 실패. userId={}, status={}",
                    userId, e.getStatus().getCode(), e);
            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        } catch (Exception e) {
            log.warn("wallet 잔액 조회 실패. userId={}", userId, e);
            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        }
    }

    private WalletServiceGrpc.WalletServiceBlockingStub deadlineWalletStub() {
        return walletStub.withDeadlineAfter(properties.getDeadlineMs(), TimeUnit.MILLISECONDS);
    }
}
