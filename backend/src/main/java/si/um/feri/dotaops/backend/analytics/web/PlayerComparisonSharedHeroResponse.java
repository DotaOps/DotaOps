package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

public record PlayerComparisonSharedHeroResponse(
        UUID heroId,
        Integer dotaHeroId,
        String heroName,
        PlayerComparisonHeroStatsResponse profileA,
        PlayerComparisonHeroStatsResponse profileB,
        PlayerComparisonHeroDeltaResponse delta
) {
}
