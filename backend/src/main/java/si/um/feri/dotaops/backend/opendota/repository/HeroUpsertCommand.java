package si.um.feri.dotaops.backend.opendota.repository;

import java.util.List;

public record HeroUpsertCommand(
        int dotaHeroId,
        String name,
        String localizedName,
        String slug,
        List<String> roles,
        String imageUrl,
        String iconUrl
) {

    public HeroUpsertCommand {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
