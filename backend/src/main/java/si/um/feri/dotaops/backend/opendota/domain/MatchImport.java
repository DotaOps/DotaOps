package si.um.feri.dotaops.backend.opendota.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchImport(
        UUID id,
        UUID matchId,
        UUID matchGameId,
        String dotaMatchId,
        MatchImportStatus status,
        UUID requestedBy,
        OpenDotaErrorCode errorCode,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
