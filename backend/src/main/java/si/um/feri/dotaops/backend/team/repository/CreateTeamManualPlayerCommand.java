package si.um.feri.dotaops.backend.team.repository;

import java.util.UUID;

public record CreateTeamManualPlayerCommand(
        UUID teamId,
        String displayName,
        String nickname,
        String note
) {
}
