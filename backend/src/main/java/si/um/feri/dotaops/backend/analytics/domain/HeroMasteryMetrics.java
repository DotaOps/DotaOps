package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record HeroMasteryMetrics(
        UUID profileId,
        UUID heroId,
        String heroName,
        int games,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal kda,
        BigDecimal avgGoldPerMin,
        BigDecimal avgXpPerMin,
        BigDecimal avgLastHits,
        BigDecimal avgDenies,
        BigDecimal avgNetWorth,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage,
        BigDecimal avgHeroHealing,
        BigDecimal avgLevel
) {
}
