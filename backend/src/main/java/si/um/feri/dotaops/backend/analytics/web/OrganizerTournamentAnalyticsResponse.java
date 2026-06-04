package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrganizerTournamentAnalyticsResponse(
        UUID tournamentId,
        int gamesProcessed,
        int matchesWithoutImport,
        BigDecimal importCoveragePercent,
        Integer avgDurationSeconds,
        TournamentMetricsResponse tournamentSummary,
        List<TeamMetricsResponse> topTeams,
        List<HeroMetricsResponse> heroMetrics,
        List<TeamMetricsResponse> teamComparison,
        List<RecentImportResponse> recentImports
) {
}
