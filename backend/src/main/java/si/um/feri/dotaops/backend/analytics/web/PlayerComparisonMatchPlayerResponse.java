package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerComparisonMatch;

public record PlayerComparisonMatchPlayerResponse(
        UUID profileId,
        UUID teamId,
        String teamName,
        UUID heroId,
        Integer dotaHeroId,
        String heroName,
        Boolean won,
        int kills,
        int deaths,
        int assists,
        BigDecimal kda,
        Integer goldPerMin,
        Integer xpPerMin,
        Integer lastHits,
        Integer denies,
        Integer netWorth,
        Integer heroDamage,
        Integer towerDamage,
        Integer heroHealing,
        String teamSide
) {

    public static PlayerComparisonMatchPlayerResponse from(PlayerComparisonMatch.PlayerMatchStats stats) {
        return new PlayerComparisonMatchPlayerResponse(
                stats.profileId(),
                stats.teamId(),
                stats.teamName(),
                stats.heroId(),
                stats.dotaHeroId(),
                stats.heroName(),
                stats.won(),
                stats.kills(),
                stats.deaths(),
                stats.assists(),
                stats.kda(),
                stats.goldPerMin(),
                stats.xpPerMin(),
                stats.lastHits(),
                stats.denies(),
                stats.netWorth(),
                stats.heroDamage(),
                stats.towerDamage(),
                stats.heroHealing(),
                stats.teamSide());
    }
}
