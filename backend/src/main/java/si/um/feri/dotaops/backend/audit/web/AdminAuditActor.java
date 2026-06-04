package si.um.feri.dotaops.backend.audit.web;

import java.util.UUID;

public record AdminAuditActor(
        UUID profileId,
        String nickname,
        String displayName,
        String role
) {
}
