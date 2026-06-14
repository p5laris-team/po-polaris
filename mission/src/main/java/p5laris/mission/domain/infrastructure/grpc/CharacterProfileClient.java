package p5laris.mission.domain.infrastructure.grpc;

import com.p5laris.proto.character.v1.CharacterServiceGrpc;
import com.p5laris.proto.character.v1.GetMyCharacterRequest;
import com.p5laris.proto.character.v1.GetMyCharacterResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * mission 모듈에서 캐릭터 말투 생성을 위해 character 모듈의 활성 캐릭터 정보를 조회하는 adapter다.
 *
 * character 모듈 장애 때문에 미션 생성 자체가 막히면 MVP 핵심 루프가 끊기므로,
 * 조회 실패 시 Optional.empty()를 반환하고 mission은 template fallback 문구로 계속 진행한다.
 */
@Slf4j
@Component
public class CharacterProfileClient {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    /**
     * 로그인 유저의 활성 캐릭터 타입 코드를 조회한다.
     *
     * 예: NOVA, MUMU, JJORY. 요청 characterId와 활성 캐릭터 ID가 다르면 잘못된 말투를 붙이지 않기 위해 비어 있는 결과를 반환한다.
     */
    public Optional<CharacterProfileSnapshot> findActiveCharacterProfile(Long userId, Long characterId) {
        try {
            GetMyCharacterResponse response = characterStub.getMyCharacter(
                    GetMyCharacterRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );
            if (!response.getActive() || response.getCharacterTypeCode().isBlank()) {
                return Optional.empty();
            }
            if (!Objects.equals(response.getId(), characterId)) {
                return Optional.empty();
            }
            return Optional.of(new CharacterProfileSnapshot(
                    response.getCharacterTypeCode(),
                    response.getName()
            ));
        } catch (StatusRuntimeException e) {
            log.warn("활성 캐릭터 프로필 조회 실패. userId={}, status={}",
                    userId, e.getStatus().getCode(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("활성 캐릭터 프로필 조회 실패. userId={}", userId, e);
            return Optional.empty();
        }
    }

    public record CharacterProfileSnapshot(
            String characterTypeCode,
            String characterName
    ) {
    }
}
