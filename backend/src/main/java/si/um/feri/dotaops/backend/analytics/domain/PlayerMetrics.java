package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerMetrics(
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
}
