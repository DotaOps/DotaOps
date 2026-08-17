package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PlayerComparisonHeadlineMetrics(
        UUID profileId,
        String displayName,
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
        BigDecimal avgLastHits,
        BigDecimal avgDenies,
        BigDecimal avgNetWorth,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage,
        BigDecimal avgHeroHealing
) {
}
