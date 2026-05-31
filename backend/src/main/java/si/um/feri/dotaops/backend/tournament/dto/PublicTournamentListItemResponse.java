package si.um.feri.dotaops.backend.tournament.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentListItem;

public record PublicTournamentListItemResponse(
        UUID id,
        String slug,
        String title,
        String status,
        String format,
        String game,
        String description,
        String prizePool,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime registrationOpensAt,
        OffsetDateTime registrationClosesAt,
        String timezone,
        int maxTeams,
        int teamSize,
        int teamCount,
        int groupCount,
        int matchCount,
        int finishedMatchCount,
        String organizer,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {

    private static final String GAME = "Dota 2";

    public PublicTournamentListItemResponse(
            UUID id,
            String slug,
            String title,
            String status,
            String format,
            String game,
            String description,
            String prizePool,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime registrationOpensAt,
            OffsetDateTime registrationClosesAt,
            String timezone,
            int maxTeams,
            int teamCount,
            int groupCount,
            int matchCount,
            int finishedMatchCount,
            String organizer,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt
    ) {
        this(
                id,
                slug,
                title,
                status,
                format,
                game,
                description,
                prizePool,
                startsAt,
                endsAt,
                registrationOpensAt,
                registrationClosesAt,
                timezone,
                maxTeams,
                5,
                teamCount,
                groupCount,
                matchCount,
                finishedMatchCount,
                organizer,
                publishedAt,
                createdAt);
    }

    public static PublicTournamentListItemResponse from(PublicTournamentListItem item) {
        return new PublicTournamentListItemResponse(
                item.id(),
                item.slug(),
                item.title(),
                item.status().databaseValue(),
                item.format().databaseValue(),
                GAME,
                item.description(),
                item.prizePool(),
                item.startsAt(),
                item.endsAt(),
                item.registrationOpensAt(),
                item.registrationClosesAt(),
                item.timezone(),
                item.maxTeams(),
                item.teamSize(),
                item.teamCount(),
                item.groupCount(),
                item.matchCount(),
                item.finishedMatchCount(),
                item.organizerNickname(),
                item.publishedAt(),
                item.createdAt());
    }
}
