package p5laris.gateway.domain.home.infrastructure.grpc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.home.api.dto.HomeDto;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.infrastructure.grpc.WalletGatewayService;
import p5laris.gateway.domain.user.api.dto.WalletDto;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;
import p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse;
import p5laris.gateway.domain.character.api.dto.MyCharacterResponse;
import p5laris.gateway.domain.character.api.dto.CharacterStatusResponse;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.notification.infrastructure.grpc.NotificationGatewayService;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeGatewayService {

    private final UserGatewayService userGatewayService;
    private final WalletGatewayService walletGatewayService;
    private final CharacterGatewayService characterGatewayService;
    private final MissionGatewayService missionGatewayService;
    private final NotificationGatewayService notificationGatewayService;

    public HomeDto.Response getHomeData(Long userId) {
        // 1. User 정보 조회 (필수)
        UserDto userDto = userGatewayService.getUser(userId);
        HomeDto.UserSummary userSummary = HomeDto.UserSummary.builder()
                .id(userDto.getId())
                .nickname(userDto.getNickname())
                .build();

        // 2. Wallet 정보 조회 (필수)
        WalletDto.Response walletDto = walletGatewayService.getMyWallet(userId);
        HomeDto.WalletSummary walletSummary = HomeDto.WalletSummary.builder()
                .starPiece(walletDto.getStarPiece())
                .build();

        // 3. Character 정보 조회 (선택)
        HomeDto.CharacterSummary characterSummary = null;
        MyCharacterResponse myChar = null;
        try {
            myChar = characterGatewayService.getMyCharacter(userId);
        } catch (Exception e) {
            log.warn("Failed to get my character for userId: {}. Character will be null in home response. Error: {}", userId, e.getMessage());
        }

        if (myChar != null && myChar.id() != null && myChar.id() > 0) {
            // Character Status 조회 (선택)
            HomeDto.StatesSummary statesSummary = null;
            CharacterGrowthResponse growth = myChar.growth();
            try {
                CharacterStatusResponse statusRes = characterGatewayService.getCharacterStatus(myChar.id(), userId);
                if (statusRes != null && statusRes.states() != null) {
                    var s = statusRes.states();
                    statesSummary = HomeDto.StatesSummary.builder()
                            .hunger(HomeDto.StateDetail.builder()
                                    .value(s.hunger().value())
                                    .label(s.hunger().label())
                                    .grade(s.hunger().grade())
                                    .build())
                            .energy(HomeDto.StateDetail.builder()
                                    .value(s.energy().value())
                                    .label(s.energy().label())
                                    .grade(s.energy().grade())
                                    .build())
                            .affection(HomeDto.StateDetail.builder()
                                    .value(s.affection().value())
                                    .label(s.affection().label())
                                    .grade(s.affection().grade())
                                    .build())
                            .build();
                }
                if (statusRes != null && statusRes.growth() != null) {
                    growth = statusRes.growth();
                }
            } catch (Exception e) {
                log.warn("Failed to get character status for characterId: {}. States will be null in home response. Error: {}", myChar.id(), e.getMessage());
            }

            characterSummary = HomeDto.CharacterSummary.builder()
                    .id(myChar.id())
                    .name(myChar.name())
                    .characterTypeCode(myChar.characterTypeCode())
                    .currentAssetUrl(myChar.currentAssetUrl())
                    .states(statesSummary)
                    .growth(growth)
                    .build();
        }

        // 4. Mission 정보 조회 (선택)
        HomeDto.CurrentMissionSummary currentMissionSummary = null;
        try {
            MissionDto.MissionResponse missionResponse = missionGatewayService.getCurrentMission(userId);
            if (missionResponse != null && missionResponse.id() != null && missionResponse.id() > 0) {
                currentMissionSummary = HomeDto.CurrentMissionSummary.builder()
                        .id(missionResponse.id())
                        .title(missionResponse.title())
                        .characterMessage(missionResponse.characterMessage())
                        .status(missionResponse.status())
                        .rewardStarPiece(missionResponse.rewardStarPiece())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to get current mission for userId: {}. Mission will be null in home response. Error: {}", userId, e.getMessage());
        }

        // 5. Notifications 정보 조회
        int unreadCount = 0;
        try {
            unreadCount = notificationGatewayService.getUnreadNotificationCount(userId);
        } catch (Exception e) {
            log.warn("Failed to get unread notification count for userId: {}. unreadCount will be 0. Error: {}", userId, e.getMessage());
        }

        HomeDto.NotificationSummary notificationSummary = HomeDto.NotificationSummary.builder()
                .unreadCount(unreadCount)
                .build();

        return HomeDto.Response.builder()
                .user(userSummary)
                .wallet(walletSummary)
                .character(characterSummary)
                .currentMission(currentMissionSummary)
                .notifications(notificationSummary)
                .build();
    }
}
