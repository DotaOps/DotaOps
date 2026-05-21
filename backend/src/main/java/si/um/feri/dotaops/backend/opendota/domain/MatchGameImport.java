package si.um.feri.dotaops.backend.opendota.domain;

import java.time.OffsetDateTime;

public record MatchGameImport(
        String dotaMatchId,
        Integer durationSeconds,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Boolean radiantWin,
        Integer gameMode,
        Integer lobbyType,
        Integer radiantScore,
        Integer direScore,
        String winnerSide,
        String rawResponse,
        String normalizedPayload
) {
}
