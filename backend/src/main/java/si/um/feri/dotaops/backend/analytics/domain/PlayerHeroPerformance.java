package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlayerHeroPerformance(
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
}
