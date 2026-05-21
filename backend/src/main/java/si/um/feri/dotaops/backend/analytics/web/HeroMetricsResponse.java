package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.HeroMetrics;

public record HeroMetricsResponse(
        UUID heroId,
        Integer dotaHeroId,
        String name,
        String localizedName,
        String imageUrl,
        String iconUrl,
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
        BigDecimal kda,
        BigDecimal avgGpm,
        BigDecimal avgXpm,
        BigDecimal avgHeroDamage
) {

    public static HeroMetricsResponse from(HeroMetrics metrics) {
        return new HeroMetricsResponse(
                metrics.heroId(),
                metrics.dotaHeroId(),
                metrics.name(),
                metrics.localizedName(),
                metrics.imageUrl(),
                metrics.iconUrl(),
                metrics.tournamentId(),
                metrics.tournamentName(),
                metrics.gamesPlayed(),
                metrics.wins(),
                metrics.losses(),
                metrics.winRate(),
                metrics.totalKills(),
                metrics.totalDeaths(),
                metrics.totalAssists(),
                metrics.avgKills(),
                metrics.avgDeaths(),
                metrics.avgAssists(),
                metrics.kda(),
                metrics.avgGpm(),
                metrics.avgXpm(),
                metrics.avgHeroDamage());
    }
}
