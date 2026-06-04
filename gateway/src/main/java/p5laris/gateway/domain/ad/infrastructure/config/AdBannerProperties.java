package p5laris.gateway.domain.ad.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ad.banner")
public class AdBannerProperties {

    private boolean enabled;
    private String clientId;
    private String bottomWebSlotId;
    private int refreshSeconds = 60;
    private int reservedHeightPx = 64;
}
