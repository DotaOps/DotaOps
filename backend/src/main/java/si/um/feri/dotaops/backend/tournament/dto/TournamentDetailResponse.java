package si.um.feri.dotaops.backend.tournament.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.Tournament;

public record TournamentDetailResponse(
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
        String rules,
        TournamentSettingsDto settings
) {

    public TournamentDetailResponse(
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
            String rules,
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
                rules,
                settings);
    }

    public static TournamentDetailResponse from(Tournament tournament) {
        return new TournamentDetailResponse(
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
                tournament.rules(),
                TournamentSettingsDto.from(tournament.settings()));
    }
}
