package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record PlayerComparisonMetricDeltaResponse(
        Integer gamesPlayed,
        Integer wins,
        Integer losses,
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
