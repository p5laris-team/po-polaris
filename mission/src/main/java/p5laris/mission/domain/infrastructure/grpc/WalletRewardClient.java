package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.user.v1.EarnStarPieceRequest;
import com.p5laris.proto.user.v1.EarnStarPieceResponse;
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
 * mission 모듈에서 user/wallet 모듈로 별조각 보상 지급을 요청하는 gRPC adapter다.
 *
 * MissionService가 gRPC 세부 메시지를 직접 알지 않도록 이 클래스가 EarnStarPiece 계약을 감싼다.
 * 외부 모듈 호출 실패는 mission 도메인 예외로 바꿔서 상위 흐름이 같은 방식으로 처리하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRewardClient {

    private static final String MISSION_REWARD_REASON = "MISSION_REWARD";
    private static final String MISSION_REF_TYPE = "MISSION";

    private final MissionRewardWalletProperties properties;

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    /**
     * 미션 완료 보상을 wallet 모듈에 지급 요청한다.
     *
     * idempotencyKey는 MISSION_REWARD:{missionId} 형식으로 고정해 같은 미션 보상이 중복 지급되지 않게 한다.
     */
    public WalletRewardResult earnMissionReward(
            Long userId,
            Long missionId,
            int rewardStarPiece,
            String idempotencyKey
    ) {
        try {
            EarnStarPieceResponse response = deadlineWalletStub().earnStarPiece(
                    EarnStarPieceRequest.newBuilder()
                            .setUserId(userId)
                            .setAmount(rewardStarPiece)
                            .setReason(MISSION_REWARD_REASON)
                            .setRefType(MISSION_REF_TYPE)
                            .setRefId(missionId)
                            .setIdempotencyKey(idempotencyKey)
                            .build()
            );

            return new WalletRewardResult(response.getStarPiece(), response.getTransactionId());
        } catch (StatusRuntimeException e) {
            log.warn("미션 보상 별조각 지급 실패. userId={}, missionId={}, status={}",
                    userId, missionId, e.getStatus().getCode(), e);
            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        } catch (Exception e) {
            log.warn("미션 보상 별조각 지급 실패. userId={}, missionId={}", userId, missionId, e);
            throw new MissionException(MissionErrorCode.MISSION_REWARD_FAILED);
        }
    }

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
