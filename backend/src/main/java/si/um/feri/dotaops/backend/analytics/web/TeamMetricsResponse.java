package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.TeamMetrics;

public record TeamMetricsResponse(
        UUID teamId,
        String teamName,
        UUID tournamentId,
        String tournamentName,
        int gamesPlayed,
        int wins,
        int losses,
        BigDecimal winRate,
        int totalKills,
        int totalDeaths,
        int totalAssists,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal avgKda,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage
) {

    public static TeamMetricsResponse from(TeamMetrics metrics) {
        return new TeamMetricsResponse(
                metrics.teamId(),
                metrics.teamName(),
                metrics.tournamentId(),
                metrics.tournamentName(),
                metrics.gamesPlayed(),
                metrics.wins(),
                metrics.losses(),
                metrics.winRate(),
                metrics.totalKills(),
                metrics.totalDeaths(),
                metrics.totalAssists(),
                metrics.avgKills(),
                metrics.avgDeaths(),
                metrics.avgAssists(),
                metrics.avgKda(),
                metrics.avgGpm(),
                metrics.avgXpm(),
                metrics.avgHeroDamage());
    }
}
