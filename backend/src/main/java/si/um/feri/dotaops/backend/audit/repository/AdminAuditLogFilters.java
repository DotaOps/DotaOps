package si.um.feri.dotaops.backend.audit.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.audit.domain.AdminAuditAction;

public record AdminAuditLogFilters(
        String tableName,
        UUID recordId,
        UUID actorProfileId,
        String actorQuery,
        AdminAuditAction action,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
