package p5laris.gateway.domain.home.api.dto;

import lombok.Builder;
import lombok.Getter;
import p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse;


public class HomeDto {

    @Getter
    @Builder
    public static class Response {
        private UserSummary user;
        private WalletSummary wallet;
        private CharacterSummary character;
        private CurrentMissionSummary currentMission;
        private NotificationSummary notifications;
    }

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String nickname;
    }

    @Getter
    @Builder
    public static class WalletSummary {
        private int starPiece;
    }

    @Getter
    @Builder
    public static class CharacterSummary {
        private Long id;
        private String name;
        private String characterTypeCode;
        private String currentAssetUrl;
        private StatesSummary states;
        private CharacterGrowthResponse growth;
    }

    @Getter
    @Builder
    public static class StatesSummary {
        private StateDetail hunger;
        private StateDetail energy;
        private StateDetail affection;
    }

    @Getter
    @Builder
    public static class StateDetail {
        private int value;
        private String label;
        private String grade;
    }

    @Getter
    @Builder
    public static class CurrentMissionSummary {
        private Long id;
        private String title;
        private String characterMessage;
        private String status;
        private int rewardStarPiece;
    }

    @Getter
    @Builder
    public static class NotificationSummary {
        private int unreadCount;
    }
}
