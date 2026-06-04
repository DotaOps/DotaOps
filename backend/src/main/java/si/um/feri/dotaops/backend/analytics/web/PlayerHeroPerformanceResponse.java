package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerHeroPerformance;

public record PlayerHeroPerformanceResponse(
        UUID heroId,
        Integer dotaHeroId,
        String heroName,
        int matches,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal avgKda,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage,
        BigDecimal avgHeroHealing,
        BigDecimal avgLastHits,
        BigDecimal avgDenies,
        UUID recentMatchId,
        UUID recentMatchGameId,
        String recentDotaMatchId,
        OffsetDateTime recentPlayedAt,
        UUID bestMatchId,
        UUID bestMatchGameId,
        String bestDotaMatchId,
        OffsetDateTime bestPlayedAt,
        BigDecimal bestKda
) {

    public static PlayerHeroPerformanceResponse from(PlayerHeroPerformance performance) {
        return new PlayerHeroPerformanceResponse(
                performance.heroId(),
                performance.dotaHeroId(),
                performance.heroName(),
                performance.matches(),
                performance.wins(),
                performance.losses(),
                performance.winRate(),
                performance.avgKills(),
                performance.avgDeaths(),
                performance.avgAssists(),
                performance.avgKda(),
                performance.avgGpm(),
                performance.avgXpm(),
                performance.avgHeroDamage(),
                performance.avgTowerDamage(),
                performance.avgHeroHealing(),
                performance.avgLastHits(),
                performance.avgDenies(),
                performance.recentMatchId(),
                performance.recentMatchGameId(),
                performance.recentDotaMatchId(),
                performance.recentPlayedAt(),
                performance.bestMatchId(),
                performance.bestMatchGameId(),
                performance.bestDotaMatchId(),
                performance.bestPlayedAt(),
                performance.bestKda());
    }
}
