package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.PlayerComparisonMatch;

public record PlayerComparisonMatchResponse(
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
        PlayerComparisonMatchPlayerResponse profileA,
        PlayerComparisonMatchPlayerResponse profileB
) {

    public static PlayerComparisonMatchResponse from(PlayerComparisonMatch match) {
        return new PlayerComparisonMatchResponse(
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
                match.winnerTeamId(),
                match.winnerSide(),
                PlayerComparisonMatchPlayerResponse.from(match.profileA()),
                PlayerComparisonMatchPlayerResponse.from(match.profileB()));
    }
}
