package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.character.v1.CharacterGrowth;
import com.p5laris.proto.character.v1.CharacterServiceGrpc;
import com.p5laris.proto.character.v1.GrantCharacterExpRequest;
import com.p5laris.proto.character.v1.GrantCharacterExpResponse;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.config.MissionCharacterExpProperties;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterExpClient {

    public static final String SOURCE_TYPE_MISSION_COMPLETION = "MISSION_COMPLETION";

    private final MissionCharacterExpProperties properties;

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    public CharacterExpGrantResult grantMissionCompletionExp(
            Long userId,
            Long characterId,
            Long missionId,
            int expAmount,
            String idempotencyKey
    ) {
        try {
            GrantCharacterExpResponse response = deadlineCharacterStub().grantCharacterExp(
                    GrantCharacterExpRequest.newBuilder()
                            .setUserId(userId)
                            .setCharacterId(characterId)
                            .setSourceType(SOURCE_TYPE_MISSION_COMPLETION)
                            .setSourceId(missionId)
                            .setExpAmount(expAmount)
                            .setIdempotencyKey(idempotencyKey)
                            .build()
            );

            return new CharacterExpGrantResult(
                    response.getCharacterId(),
                    response.getExpGained(),
                    toGrowth(response.getBeforeGrowth()),
                    toGrowth(response.getAfterGrowth()),
                    response.getLevelUp(),
                    response.getAlreadyProcessed()
            );
        } catch (StatusRuntimeException e) {
            log.warn("미션 완료 캐릭터 경험치 지급 실패. userId={}, characterId={}, missionId={}, status={}",
                    userId, characterId, missionId, e.getStatus().getCode(), e);
            throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
        } catch (Exception e) {
            log.warn("미션 완료 캐릭터 경험치 지급 실패. userId={}, characterId={}, missionId={}",
                    userId, characterId, missionId, e);
            throw new MissionException(MissionErrorCode.MISSION_CHARACTER_EXP_FAILED);
        }
    }

    private CharacterServiceGrpc.CharacterServiceBlockingStub deadlineCharacterStub() {
        return characterStub.withDeadlineAfter(properties.getDeadlineMs(), TimeUnit.MILLISECONDS);
    }

    private MissionCharacterGrowth toGrowth(CharacterGrowth growth) {
        if (growth == null) {
            return null;
        }
        return new MissionCharacterGrowth(
                growth.getLevel(),
                growth.getExp(),
                growth.getCurrentLevelExp(),
                growth.getNextLevelExp(),
                growth.getExpToNextLevel(),
                growth.getProgressPercent(),
                growth.getGrowthStage(),
                growth.getGrowthStageLabel(),
                growth.getMaxLevel()
        );
    }
}
