package p5laris.gateway.domain.ad.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p5laris.gateway.domain.ad.api.dto.AdDto;
import p5laris.gateway.domain.ad.infrastructure.config.AdBannerProperties;

@Service
@RequiredArgsConstructor
public class AdConfigService {

    private static final String PROVIDER = "ADSENSE";
    private static final String FORMAT = "ANCHOR";
    private static final String LAYOUT = "BOTTOM_FIXED";

    private final AdBannerProperties properties;

    public AdDto.BannerConfigResponse getBannerConfig(String placement, String path) {
        String normalizedPlacement = normalizePlacement(placement);
        String slotId = resolveSlotId(normalizedPlacement);
        boolean enabled = properties.isEnabled()
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(slotId)
                && isSupportedPlacement(normalizedPlacement);

        return new AdDto.BannerConfigResponse(
                enabled,
                normalizedPlacement,
                PROVIDER,
                enabled ? properties.getClientId() : null,
                enabled ? slotId : null,
                FORMAT,
                LAYOUT,
                enabled ? properties.getRefreshSeconds() : 0,
                enabled ? properties.getReservedHeightPx() : 0,
                new AdDto.Policy(false, true, true)
        );
    }

    private String normalizePlacement(String placement) {
        if (!StringUtils.hasText(placement)) {
            return "BOTTOM_WEB";
        }
        return placement.trim().toUpperCase();
    }

    private boolean isSupportedPlacement(String placement) {
        return "BOTTOM_WEB".equals(placement);
    }

    private String resolveSlotId(String placement) {
        if ("BOTTOM_WEB".equals(placement)) {
            return properties.getBottomWebSlotId();
        }
        return null;
    }
}
