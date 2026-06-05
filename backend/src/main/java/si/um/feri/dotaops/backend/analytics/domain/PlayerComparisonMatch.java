package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlayerComparisonMatch(
        UUID matchId,
        UUID matchGameId,
        String dotaMatchId,
        UUID tournamentId,
        String tournamentName,
        OffsetDateTime playedAt,
        UUID teamAId,
        String teamAName,
        UUID teamBId,
        String teamBName,
        UUID winnerTeamId,
        String winnerSide,
        PlayerMatchStats profileA,
        PlayerMatchStats profileB
) {

    public record PlayerMatchStats(
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
    }
}
