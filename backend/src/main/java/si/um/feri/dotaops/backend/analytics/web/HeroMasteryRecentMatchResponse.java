package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;

public record HeroMasteryRecentMatchResponse(
        UUID matchId,
        UUID matchGameId,
        String dotaMatchId,
        OffsetDateTime playedAt,
        UUID heroId,
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
        Integer level,
        Integer durationSeconds,
        String teamSide,
        Integer radiantScore,
        Integer direScore,
        String winnerSide,
        BigDecimal contextWeight,
        ContextWeightClassification contextClassification,
        List<ContextWeightReason> contextReasons,
        String contextMessage
) {
}
