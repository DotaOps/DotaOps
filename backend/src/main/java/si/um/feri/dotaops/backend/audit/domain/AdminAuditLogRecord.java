package si.um.feri.dotaops.backend.audit.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAuditLogRecord(
        UUID id,
        OffsetDateTime createdAt,
        UUID actorProfileId,
        String actorNickname,
        AdminAuditAction action,
        String tableName,
        UUID recordId,
        String previousRowJson,
        String newRowJson
) {
}
