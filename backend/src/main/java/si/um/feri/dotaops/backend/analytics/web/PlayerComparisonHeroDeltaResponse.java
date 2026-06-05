package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record PlayerComparisonHeroDeltaResponse(
        Integer gamesPlayed,
        BigDecimal winRate,
        BigDecimal kda,
        BigDecimal avgDeaths,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage
) {
}
