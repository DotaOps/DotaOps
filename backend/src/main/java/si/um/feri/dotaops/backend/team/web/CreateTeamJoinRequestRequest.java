package si.um.feri.dotaops.backend.team.web;

import jakarta.validation.constraints.Size;

public record CreateTeamJoinRequestRequest(
        @Size(max = 1000)
        String message
) {
}
