package si.um.feri.dotaops.backend.analytics.domain;

import java.util.UUID;

public record PickedHeroMetrics(
        UUID heroId,
        Integer dotaHeroId,
        String localizedName,
        String imageUrl,
        String iconUrl,
        int picks
) {
}
