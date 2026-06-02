package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DisbandTeamResponse(
        UUID teamId,
        String status,
        OffsetDateTime disbandedAt
) {
}
