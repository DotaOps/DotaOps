package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlayerProgressPoint(
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
}
