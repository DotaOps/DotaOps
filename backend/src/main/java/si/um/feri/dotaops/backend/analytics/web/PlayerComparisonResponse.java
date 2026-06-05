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
        List<AnalyticsMatchHistoryResponse> recentMatches,
        PlayerComparisonHeadlineResponse headlineComparison,
        List<PlayerHeroPerformanceResponse> profileAHeroDetails,
        List<PlayerHeroPerformanceResponse> profileBHeroDetails,
        List<PlayerComparisonSharedHeroResponse> sharedHeroComparisons,
        List<PlayerComparisonMatchResponse> enrichedMatchHistory,
        List<PlayerComparisonWarningResponse> warnings
) {

    public PlayerComparisonResponse(
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
        this(
                profileAId,
                profileBId,
                filters,
                playerA,
                playerB,
                players,
                profileAHeroPerformance,
                profileBHeroPerformance,
                sharedHeroes,
                recentMatches,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
