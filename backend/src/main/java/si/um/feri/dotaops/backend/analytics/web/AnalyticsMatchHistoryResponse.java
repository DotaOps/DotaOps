package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsMatchHistory;

public record AnalyticsMatchHistoryResponse(
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

    public AnalyticsMatchHistoryResponse(UUID matchId, UUID matchGameId, String dotaMatchId) {
        this(matchId, matchGameId, dotaMatchId, null, null, null, null, null, null, null, null);
    }

    public static AnalyticsMatchHistoryResponse from(AnalyticsMatchHistory match) {
        return new AnalyticsMatchHistoryResponse(
                match.matchId(),
                match.matchGameId(),
                match.dotaMatchId(),
                match.tournamentId(),
                match.tournamentName(),
                match.playedAt(),
                match.teamAId(),
                match.teamAName(),
                match.teamBId(),
                match.teamBName(),
                match.winnerTeamId());
    }
}
