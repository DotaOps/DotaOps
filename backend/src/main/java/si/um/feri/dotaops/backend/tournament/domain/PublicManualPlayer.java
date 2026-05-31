package si.um.feri.dotaops.backend.tournament.domain;

import java.util.UUID;

public record PublicManualPlayer(
        UUID id,
        String displayName,
        String nickname,
        String note
) {
}
