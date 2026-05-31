package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.team.domain.TeamJoinRequest;
import si.um.feri.dotaops.backend.team.domain.TeamJoinRequestStatus;

public record TeamJoinRequestResponse(
        UUID id,
        UUID teamId,
        String teamName,
        String teamSlug,
        UUID requesterProfileId,
        String requesterDisplayName,
        String message,
        TeamJoinRequestStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt,
        UUID resolvedByProfileId,
        String resolvedByDisplayName
) {

    public static TeamJoinRequestResponse from(TeamJoinRequest joinRequest) {
        return new TeamJoinRequestResponse(
                joinRequest.id(),
                joinRequest.teamId(),
                joinRequest.teamName(),
                joinRequest.teamSlug(),
                joinRequest.requesterProfileId(),
                joinRequest.requesterDisplayName(),
                joinRequest.message(),
                joinRequest.status(),
                joinRequest.createdAt(),
                joinRequest.updatedAt(),
                joinRequest.resolvedAt(),
                joinRequest.resolvedByProfileId(),
                joinRequest.resolvedByDisplayName());
    }
}
