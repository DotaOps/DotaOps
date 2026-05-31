package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import si.um.feri.dotaops.backend.team.domain.Team;

public record TeamResponse(
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
        List<TeamManualPlayerResponse> manualPlayers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public TeamResponse(
            UUID id,
            String name,
            String tag,
            String slug,
            UUID captainProfileId,
            String captainNickname,
            String region,
            String logoUrl,
            String description,
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
                List.of(),
                createdAt,
                updatedAt);
    }

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.id(),
                team.name(),
                team.tag(),
                team.slug(),
                team.captainProfileId(),
                team.captainNickname(),
                team.region(),
                team.logoUrl(),
                team.bannerUrl(),
                team.description(),
                List.of(),
                team.createdAt(),
                team.updatedAt());
    }

    public static TeamResponse from(Team team, List<TeamManualPlayerResponse> manualPlayers) {
        return new TeamResponse(
                team.id(),
                team.name(),
                team.tag(),
                team.slug(),
                team.captainProfileId(),
                team.captainNickname(),
                team.region(),
                team.logoUrl(),
                team.bannerUrl(),
                team.description(),
                manualPlayers == null ? List.of() : List.copyOf(manualPlayers),
                team.createdAt(),
                team.updatedAt());
    }
}
