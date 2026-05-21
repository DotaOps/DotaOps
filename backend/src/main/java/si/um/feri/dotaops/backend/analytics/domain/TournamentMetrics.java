package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TournamentMetrics(
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
        List<PickedHeroMetrics> mostPickedHeroes
) {

    public TournamentMetrics {
        mostPickedHeroes = mostPickedHeroes == null ? List.of() : List.copyOf(mostPickedHeroes);
    }
}
