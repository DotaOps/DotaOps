package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;
import java.util.UUID;

public record PlayerComparisonResponse(
        UUID profileAId,
        UUID profileBId,
        AnalyticsComparisonFiltersResponse filters,
        PlayerMetricsResponse playerA,
        PlayerMetricsResponse playerB,
        List<PlayerMetricsResponse> players,
        List<HeroMetricsResponse> profileAHeroPerformance,
        List<HeroMetricsResponse> profileBHeroPerformance,
        List<HeroMetricsResponse> sharedHeroes,
        List<AnalyticsMatchHistoryResponse> recentMatches
) {
}
