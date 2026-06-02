package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;

import si.um.feri.dotaops.backend.team.web.TeamResponse;

public record CurrentTeamAnalyticsResponse(
        TeamResponse team,
        List<TeamMetricsResponse> teamSummary,
        List<PlayerMetricsResponse> rosterPerformance,
        List<AnalyticsMatchHistoryResponse> recentTeamMatches
) {
}
