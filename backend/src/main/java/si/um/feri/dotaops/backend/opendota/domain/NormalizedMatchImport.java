package si.um.feri.dotaops.backend.opendota.domain;

import java.util.List;

public record NormalizedMatchImport(
        MatchGameImport matchGame,
        List<MatchPlayerImport> players
) {

    public NormalizedMatchImport {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
