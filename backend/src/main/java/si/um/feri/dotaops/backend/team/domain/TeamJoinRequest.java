package si.um.feri.dotaops.backend.team.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamJoinRequest(
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
}
