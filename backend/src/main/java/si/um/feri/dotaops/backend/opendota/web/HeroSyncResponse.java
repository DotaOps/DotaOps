package si.um.feri.dotaops.backend.opendota.web;

import java.time.OffsetDateTime;

public record HeroSyncResponse(
        int syncedCount,
        int insertedCount,
        int updatedCount,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
