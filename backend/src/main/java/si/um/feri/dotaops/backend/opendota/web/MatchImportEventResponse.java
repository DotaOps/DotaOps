package si.um.feri.dotaops.backend.opendota.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.opendota.domain.MatchImportEvent;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;

public record MatchImportEventResponse(
        UUID id,
        MatchImportStatus eventType,
        String message,
        OpenDotaErrorCode errorCode,
        OffsetDateTime createdAt
) {

    public static MatchImportEventResponse from(MatchImportEvent event) {
        return new MatchImportEventResponse(
                event.id(),
                event.eventType(),
                event.message(),
                event.errorCode(),
                event.createdAt());
    }
}
