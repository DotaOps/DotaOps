package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record TeamMetrics(
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
}
