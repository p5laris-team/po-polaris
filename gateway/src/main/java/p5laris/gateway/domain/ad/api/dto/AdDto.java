package p5laris.gateway.domain.ad.api.dto;

public class AdDto {

    public record BannerConfigResponse(
            boolean enabled,
            String placement,
            String provider,
            String clientId,
            String slotId,
            String format,
            String layout,
            int refreshSeconds,
            int reservedHeightPx,
            Policy policy
    ) {
    }

    public record Policy(
            boolean hideOnPaidUser,
            boolean hideOnKeyboardVisible,
            boolean hideOnSensitiveScreen
    ) {
    }
}
