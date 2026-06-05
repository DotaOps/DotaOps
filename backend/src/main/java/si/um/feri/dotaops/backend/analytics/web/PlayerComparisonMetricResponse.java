package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerComparisonHeadlineMetrics;

public record PlayerComparisonMetricResponse(
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

    public static PlayerComparisonMetricResponse from(PlayerComparisonHeadlineMetrics metrics) {
        return new PlayerComparisonMetricResponse(
                metrics.profileId(),
                metrics.displayName(),
                metrics.gamesPlayed(),
                metrics.wins(),
                metrics.losses(),
                metrics.winRate(),
                metrics.kda(),
                metrics.avgKills(),
                metrics.avgDeaths(),
                metrics.avgAssists(),
                metrics.avgGpm(),
                metrics.avgXpm(),
                metrics.avgLastHits(),
                metrics.avgDenies(),
                metrics.avgNetWorth(),
                metrics.avgHeroDamage(),
                metrics.avgTowerDamage(),
                metrics.avgHeroHealing());
    }
}
