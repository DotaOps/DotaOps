package si.um.feri.dotaops.backend.team.repository;

import java.util.UUID;

public record CreateTeamJoinRequestCommand(
        UUID teamId,
        UUID requesterProfileId,
        String message
) {
}
