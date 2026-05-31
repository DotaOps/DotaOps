package si.um.feri.dotaops.backend.team.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamManualPlayer(
        UUID id,
        UUID teamId,
        String displayName,
        String nickname,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
