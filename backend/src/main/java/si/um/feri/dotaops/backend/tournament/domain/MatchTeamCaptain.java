package si.um.feri.dotaops.backend.tournament.domain;

import java.util.UUID;

public record MatchTeamCaptain(
        UUID teamId,
        String teamName,
        UUID captainProfileId
) {
}
