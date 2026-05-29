package si.um.feri.dotaops.backend.tournament.dto;

import java.util.List;
import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentTeam;

public record PublicTeamResponse(
        UUID id,
        String name,
        String tag,
        String slug,
        String logoUrl,
        String bannerUrl,
        Integer seedNumber,
        List<PublicManualPlayerResponse> manualPlayers
) {

    public PublicTeamResponse(
            UUID id,
            String name,
            String tag,
            String slug,
            String logoUrl,
            Integer seedNumber
    ) {
        this(id, name, tag, slug, logoUrl, null, seedNumber, List.of());
    }

    public static PublicTeamResponse from(PublicTournamentTeam team) {
        if (team == null) {
            return null;
        }

        return new PublicTeamResponse(
                team.id(),
                team.name(),
                team.tag(),
                team.slug(),
                team.logoUrl(),
                team.bannerUrl(),
                team.seedNumber(),
                team.manualPlayers().stream()
                        .map(PublicManualPlayerResponse::from)
                        .toList());
    }
}
