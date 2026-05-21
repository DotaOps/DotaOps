package si.um.feri.dotaops.backend.opendota.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchImportEvent(
        UUID id,
        UUID matchImportId,
        MatchImportStatus eventType,
        String message,
        OpenDotaErrorCode errorCode,
        UUID createdBy,
        OffsetDateTime createdAt
) {
}
