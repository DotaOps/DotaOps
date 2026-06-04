package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;
import java.util.UUID;

public record TeamComparisonResponse(
        UUID teamAId,
        UUID teamBId,
        AnalyticsComparisonFiltersResponse filters,
        TeamMetricsResponse teamA,
        TeamMetricsResponse teamB,
        List<TeamMetricsResponse> teams,
        List<HeroMetricsResponse> heroMetrics,
        List<AnalyticsMatchHistoryResponse> recentMatches
) {
}
