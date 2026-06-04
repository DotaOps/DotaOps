package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerProgressPoint;

public record PlayerProgressPointResponse(
        OffsetDateTime playedAt,
        UUID matchId,
        UUID matchGameId,
        String dotaMatchId,
        UUID heroId,
        Integer dotaHeroId,
        String heroName,
        int kills,
        int deaths,
        int assists,
        BigDecimal kda,
        Integer goldPerMin,
        Integer xpPerMin,
        Integer heroDamage,
        Integer towerDamage,
        Integer heroHealing,
        Integer lastHits,
        Integer denies,
        Boolean won
) {

    public static PlayerProgressPointResponse from(PlayerProgressPoint point) {
        return new PlayerProgressPointResponse(
                point.playedAt(),
                point.matchId(),
                point.matchGameId(),
                point.dotaMatchId(),
                point.heroId(),
                point.dotaHeroId(),
                point.heroName(),
                point.kills(),
                point.deaths(),
                point.assists(),
                point.kda(),
                point.goldPerMin(),
                point.xpPerMin(),
                point.heroDamage(),
                point.towerDamage(),
                point.heroHealing(),
                point.lastHits(),
                point.denies(),
                point.won());
    }
}
