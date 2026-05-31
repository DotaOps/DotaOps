package si.um.feri.dotaops.backend.tournament.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicTournamentListItem(
        UUID id,
        String slug,
        String title,
        TournamentStatus status,
        TournamentFormat format,
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
        String organizerNickname,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt
) {
}
