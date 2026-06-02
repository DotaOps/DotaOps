package si.um.feri.dotaops.backend.team.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TransferTeamOwnershipRequest(
        @NotNull UUID newOwnerProfileId
) {
}
