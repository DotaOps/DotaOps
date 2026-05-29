package si.um.feri.dotaops.backend.team.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Team(
        UUID id,
        String name,
        String tag,
        String slug,
        UUID captainProfileId,
        String captainNickname,
        String region,
        String logoUrl,
        String bannerUrl,
        String description,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public Team(
            UUID id,
            String name,
            String tag,
            String slug,
            UUID captainProfileId,
            String captainNickname,
            String region,
            String logoUrl,
            String description,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                name,
                tag,
                slug,
                captainProfileId,
                captainNickname,
                region,
                logoUrl,
                null,
                description,
                createdBy,
                createdAt,
                updatedAt);
    }
}
