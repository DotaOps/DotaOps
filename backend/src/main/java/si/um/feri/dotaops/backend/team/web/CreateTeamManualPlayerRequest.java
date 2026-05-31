package si.um.feri.dotaops.backend.team.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamManualPlayerRequest(
        @NotBlank
        @Size(min = 1, max = 80)
        String displayName,

        @Size(max = 80)
        String nickname,

        @Size(max = 500)
        String note
) {
}
