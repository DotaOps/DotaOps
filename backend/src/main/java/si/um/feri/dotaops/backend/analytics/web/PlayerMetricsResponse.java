package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerMetrics;

public record PlayerMetricsResponse(
        UUID profileId,
        String displayName,
        UUID teamId,
        String teamName,
        UUID tournamentId,
        String tournamentName,
        int gamesPlayed,
        int wins,
        int losses,
        BigDecimal winRate,
        int kills,
        int deaths,
        int assists,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal kda,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage
) {

    public static PlayerMetricsResponse from(PlayerMetrics metrics) {
        return new PlayerMetricsResponse(
                metrics.profileId(),
                metrics.displayName(),
                metrics.teamId(),
                metrics.teamName(),
                metrics.tournamentId(),
                metrics.tournamentName(),
                metrics.gamesPlayed(),
                metrics.wins(),
                metrics.losses(),
                metrics.winRate(),
                metrics.kills(),
                metrics.deaths(),
                metrics.assists(),
                metrics.avgKills(),
                metrics.avgDeaths(),
                metrics.avgAssists(),
                metrics.kda(),
                metrics.avgGpm(),
                metrics.avgXpm(),
                metrics.avgHeroDamage());
    }
}
