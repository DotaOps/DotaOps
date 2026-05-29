package si.um.feri.dotaops.backend.tournament.domain;

import java.util.List;
import java.util.UUID;

public record PublicTournamentTeam(
        UUID id,
        String name,
        String tag,
        String slug,
        String logoUrl,
        String bannerUrl,
        Integer seedNumber,
        List<PublicManualPlayer> manualPlayers
) {
}
