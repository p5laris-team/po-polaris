package p5laris.gateway.domain.user.api.dto;

import java.util.List;

public class WeatherRegionDto {

    public record RegionItem(
            String regionCode,
            String displayName
    ) {
    }

    public record RegionListResponse(
            List<RegionItem> regions
    ) {
    }

    public record SelectedRegionResponse(
            boolean selected,
            String regionCode,
            String displayName
    ) {
    }

    public record UpdateRegionRequest(
            String regionCode
    ) {
    }
}
