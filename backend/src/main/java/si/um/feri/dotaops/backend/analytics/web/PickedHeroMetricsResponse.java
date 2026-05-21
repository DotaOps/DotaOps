package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PickedHeroMetrics;

public record PickedHeroMetricsResponse(
        UUID heroId,
        Integer dotaHeroId,
        String localizedName,
        String imageUrl,
        String iconUrl,
        int picks
) {

    public static PickedHeroMetricsResponse from(PickedHeroMetrics metrics) {
        return new PickedHeroMetricsResponse(
                metrics.heroId(),
                metrics.dotaHeroId(),
                metrics.localizedName(),
                metrics.imageUrl(),
                metrics.iconUrl(),
                metrics.picks());
    }
}
