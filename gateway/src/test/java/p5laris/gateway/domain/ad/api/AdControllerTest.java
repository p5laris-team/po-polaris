package p5laris.gateway.domain.ad.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import p5laris.gateway.domain.ad.application.AdConfigService;
import p5laris.gateway.domain.ad.infrastructure.config.AdBannerProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdControllerTest {

    private AdBannerProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new AdBannerProperties();
        AdConfigService service = new AdConfigService(properties);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdController(service))
                .build();
    }

    @Test
    void returnsDisabledConfigWhenEnvValuesAreMissing() throws Exception {
        properties.setEnabled(true);

        mockMvc.perform(get("/api/ad/v1/banner-config")
                        .param("placement", "BOTTOM_WEB")
                        .param("path", "/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.provider").value("ADSENSE"))
                .andExpect(jsonPath("$.data.clientId").doesNotExist())
                .andExpect(jsonPath("$.data.slotId").doesNotExist())
                .andExpect(jsonPath("$.data.reservedHeightPx").value(0));
    }

    @Test
    void returnsEnabledConfigWhenEnvValuesArePresent() throws Exception {
        properties.setEnabled(true);
        properties.setClientId("ca-pub-0000000000000000");
        properties.setBottomWebSlotId("0000000000");
        properties.setRefreshSeconds(45);
        properties.setReservedHeightPx(72);

        mockMvc.perform(get("/api/ad/v1/banner-config")
                        .param("placement", "bottom_web")
                        .param("path", "/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.placement").value("BOTTOM_WEB"))
                .andExpect(jsonPath("$.data.provider").value("ADSENSE"))
                .andExpect(jsonPath("$.data.clientId").value("ca-pub-0000000000000000"))
                .andExpect(jsonPath("$.data.slotId").value("0000000000"))
                .andExpect(jsonPath("$.data.format").value("ANCHOR"))
                .andExpect(jsonPath("$.data.layout").value("BOTTOM_FIXED"))
                .andExpect(jsonPath("$.data.refreshSeconds").value(45))
                .andExpect(jsonPath("$.data.reservedHeightPx").value(72))
                .andExpect(jsonPath("$.data.policy.hideOnKeyboardVisible").value(true));
    }
}
