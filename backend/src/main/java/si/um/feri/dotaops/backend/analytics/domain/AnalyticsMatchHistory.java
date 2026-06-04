package si.um.feri.dotaops.backend.analytics.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalyticsMatchHistory(
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
        UUID winnerTeamId
) {
}
