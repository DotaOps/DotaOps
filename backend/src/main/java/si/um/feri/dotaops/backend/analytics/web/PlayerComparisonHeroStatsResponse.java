package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerComparisonHeroStatsResponse(
        UUID profileId,
        int gamesPlayed,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal kda,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage,
        BigDecimal avgHeroHealing
) {
}
