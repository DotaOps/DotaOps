package si.um.feri.dotaops.backend.opendota.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Hero(
        UUID id,
        int dotaHeroId,
        String name,
        String localizedName,
        String slug,
        List<String> roles,
        String imageUrl,
        String iconUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public Hero {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
