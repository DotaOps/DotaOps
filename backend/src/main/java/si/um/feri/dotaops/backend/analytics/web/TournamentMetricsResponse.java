package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.TournamentMetrics;

public record TournamentMetricsResponse(
        UUID tournamentId,
        String tournamentName,
        int gamesPlayed,
        int teamsCount,
        int playersCount,
        int heroesPickedCount,
        Integer avgDurationSeconds,
        int totalKills,
        int totalDeaths,
        int totalAssists,
        BigDecimal avgKillsPerGame,
        BigDecimal avgKda,
        List<PickedHeroMetricsResponse> mostPickedHeroes
) {

    public static TournamentMetricsResponse from(TournamentMetrics metrics) {
        return new TournamentMetricsResponse(
                metrics.tournamentId(),
                metrics.tournamentName(),
                metrics.gamesPlayed(),
                metrics.teamsCount(),
                metrics.playersCount(),
                metrics.heroesPickedCount(),
                metrics.avgDurationSeconds(),
                metrics.totalKills(),
                metrics.totalDeaths(),
                metrics.totalAssists(),
                metrics.avgKillsPerGame(),
                metrics.avgKda(),
                metrics.mostPickedHeroes().stream()
                        .map(PickedHeroMetricsResponse::from)
                        .toList());
    }
}
