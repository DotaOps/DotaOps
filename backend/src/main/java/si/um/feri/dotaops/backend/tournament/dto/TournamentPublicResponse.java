package si.um.feri.dotaops.backend.tournament.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.Tournament;

public record TournamentPublicResponse(
        UUID id,
        String slug,
        String title,
        String status,
        String format,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime registrationOpensAt,
        OffsetDateTime registrationClosesAt,
        OffsetDateTime checkInOpensAt,
        OffsetDateTime checkInClosesAt,
        String timezone,
        int maxTeams,
        int teamSize,
        int teamsCount,
        long registrationsCount,
        String organizer,
        String prizePool,
        String description,
        TournamentSettingsDto settings
) {

    public TournamentPublicResponse(
            UUID id,
            String slug,
            String title,
            String status,
            String format,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime registrationOpensAt,
            OffsetDateTime registrationClosesAt,
            OffsetDateTime checkInOpensAt,
            OffsetDateTime checkInClosesAt,
            String timezone,
            int maxTeams,
            int teamsCount,
            long registrationsCount,
            String organizer,
            String prizePool,
            String description,
            TournamentSettingsDto settings
    ) {
        this(
                id,
                slug,
                title,
                status,
                format,
                startsAt,
                endsAt,
                registrationOpensAt,
                registrationClosesAt,
                checkInOpensAt,
                checkInClosesAt,
                timezone,
                maxTeams,
                settings == null || settings.teamSize() == null ? 5 : settings.teamSize(),
                teamsCount,
                registrationsCount,
                organizer,
                prizePool,
                description,
                settings);
    }

    public static TournamentPublicResponse from(Tournament tournament) {
        return new TournamentPublicResponse(
                tournament.id(),
                tournament.slug(),
                tournament.title(),
                tournament.status().databaseValue(),
                tournament.format().databaseValue(),
                tournament.startsAt(),
                tournament.endsAt(),
                tournament.registrationOpensAt(),
                tournament.registrationClosesAt(),
                tournament.checkInOpensAt(),
                tournament.checkInClosesAt(),
                tournament.timezone(),
                tournament.maxTeams(),
                tournament.settings().teamSize(),
                tournament.maxTeams(),
                tournament.registrationsCount(),
                tournament.organizerNickname(),
                tournament.prizePool(),
                tournament.description(),
                TournamentSettingsDto.from(tournament.settings()));
    }
}
