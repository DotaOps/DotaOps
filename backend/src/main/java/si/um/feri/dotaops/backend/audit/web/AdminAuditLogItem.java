package si.um.feri.dotaops.backend.audit.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogItem(
        UUID id,
        OffsetDateTime createdAt,
        AdminAuditActor actor,
        String action,
        String table,
        UUID recordId,
        String summary,
        List<String> changedFields,
        Map<String, Object> previousRow,
        Map<String, Object> newRow
) {
}
