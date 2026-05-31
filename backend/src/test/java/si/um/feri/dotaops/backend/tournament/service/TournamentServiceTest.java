package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentRequest;
import si.um.feri.dotaops.backend.tournament.dto.TournamentSettingsDto;
import si.um.feri.dotaops.backend.tournament.dto.UpdateTournamentRequest;
import si.um.feri.dotaops.backend.tournament.mapper.TournamentSettingsMapper;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;
import si.um.feri.dotaops.backend.tournament.repository.UpdateTournamentCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-06-01T18:00:00Z");

    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final TournamentSettingsMapper settingsMapper = new TournamentSettingsMapper();
    private final TournamentService tournamentService = new TournamentService(
            tournamentRepository,
            currentUserProvider,
            databaseActorContext,
            settingsMapper);

    @Test
    void organizerCanCreateDraftTournamentWithDefaults() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.create(any())).thenReturn(tournament(TournamentStatus.DRAFT, settings(), STARTS_AT));

        tournamentService.createTournament(new CreateTournamentRequest(
                "  Mid Wars Open  ",
                null,
                null,
                "  Public qualifier  ",
                null,
                "TBD",
                null,
                STARTS_AT,
                null,
                STARTS_AT.minusDays(7),
                STARTS_AT.minusDays(1),
                null,
                null,
                null,
                null));

        ArgumentCaptor<CreateTournamentCommand> captor = ArgumentCaptor.forClass(CreateTournamentCommand.class);
        verify(databaseActorContext).apply(actor);
        verify(tournamentRepository).create(captor.capture());

        assertThat(captor.getValue().title()).isEqualTo("Mid Wars Open");
        assertThat(captor.getValue().slug()).isEqualTo("mid-wars-open");
        assertThat(captor.getValue().format()).isEqualTo(TournamentFormat.SINGLE_ELIMINATION);
        assertThat(captor.getValue().organizerProfileId()).isEqualTo(ORGANIZER_PROFILE_ID);
        assertThat(captor.getValue().createdBy()).isEqualTo(AUTH_USER_ID);
        assertThat(captor.getValue().maxTeams()).isEqualTo(TournamentSettings.DEFAULT_MAX_TEAMS);
        assertThat(captor.getValue().settingsJson()).contains("\"teamSize\":5");
    }

    @Test
    void nonOrganizerCannotCreateTournament() {
        when(currentUserProvider.requireActor()).thenReturn(actor(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> tournamentService.createTournament(new CreateTournamentRequest(
                "Mid Wars Open",
                null,
                null,
                null,
                null,
                null,
                null,
                STARTS_AT,
                null,
                null,
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only organizers or admins can create tournaments.");
    }

    @Test
    void ownerCanUpdateTournament() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(tournamentRepository.update(eq(TOURNAMENT_ID), any())).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));

        tournamentService.updateTournament(TOURNAMENT_ID, new UpdateTournamentRequest(
                "Mid Wars Invitational",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        ArgumentCaptor<UpdateTournamentCommand> captor = ArgumentCaptor.forClass(UpdateTournamentCommand.class);
        verify(databaseActorContext).apply(actor);
        verify(tournamentRepository).update(eq(TOURNAMENT_ID), captor.capture());
        assertThat(captor.getValue().titlePresent()).isTrue();
        assertThat(captor.getValue().title()).isEqualTo("Mid Wars Invitational");
    }

    @Test
    void nonOwnerCannotUpdateTournament() {
        AuthenticatedActor actor = actor(OTHER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, OTHER_PROFILE_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> tournamentService.updateTournament(TOURNAMENT_ID, new UpdateTournamentRequest(
                "Mid Wars Invitational",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can manage this tournament.");
    }

    @Test
    void adminCanUpdateTournament() {
        AuthenticatedActor actor = actor(OTHER_PROFILE_ID, ProfileRole.ADMIN);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, OTHER_PROFILE_ID, true)).thenReturn(true);
        when(tournamentRepository.update(eq(TOURNAMENT_ID), any())).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));

        tournamentService.updateTournament(TOURNAMENT_ID, new UpdateTournamentRequest(
                null,
                null,
                TournamentFormat.GROUPS_PLAYOFF,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        verify(tournamentRepository).update(eq(TOURNAMENT_ID), any());
    }

    @Test
    void ownerCanPublishTournament() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        Tournament tournament = tournament(TournamentStatus.DRAFT, settings(), STARTS_AT);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(tournamentRepository.publish(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings(),
                STARTS_AT)));

        var response = tournamentService.publishTournament(TOURNAMENT_ID);

        assertThat(response.status()).isEqualTo("published");
        verify(databaseActorContext).apply(actor);
    }

    @Test
    void nonOwnerCannotPublishTournament() {
        AuthenticatedActor actor = actor(OTHER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, OTHER_PROFILE_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> tournamentService.publishTournament(TOURNAMENT_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerCanArchiveTournament() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(tournamentRepository.archive(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.ARCHIVED,
                settings(),
                STARTS_AT)));

        var response = tournamentService.archiveTournament(TOURNAMENT_ID);

        assertThat(response.status()).isEqualTo("archived");
        verify(databaseActorContext).apply(actor);
    }

    @Test
    void publishRejectsImpossibleDateSequence() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        Tournament tournament = tournament(
                TournamentStatus.DRAFT,
                settings(),
                STARTS_AT,
                STARTS_AT.minusDays(4),
                STARTS_AT.plusHours(1),
                null,
                null);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> tournamentService.publishTournament(TOURNAMENT_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Registration close time must be before or equal to tournament start time.");
    }

    @Test
    void createRejectsInvalidSettings() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> tournamentService.createTournament(new CreateTournamentRequest(
                "Mid Wars Open",
                null,
                TournamentFormat.SINGLE_ELIMINATION,
                null,
                null,
                null,
                null,
                STARTS_AT,
                null,
                null,
                null,
                null,
                null,
                null,
                new TournamentSettingsDto(8, 2, 4, 1, TournamentFormat.SINGLE_ELIMINATION, false, true))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament settings teamSize must be 1, 3, or 5.");
    }

    @Test
    void createAllowsSupportedCasualTeamSizes() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.create(any())).thenReturn(tournament(
                TournamentStatus.DRAFT,
                new TournamentSettings(8, 2, 3, 1, TournamentFormat.SINGLE_ELIMINATION, false, true),
                STARTS_AT));

        tournamentService.createTournament(new CreateTournamentRequest(
                "Tri Lane Cup",
                null,
                TournamentFormat.SINGLE_ELIMINATION,
                null,
                null,
                null,
                null,
                STARTS_AT,
                null,
                null,
                null,
                null,
                null,
                null,
                new TournamentSettingsDto(8, 2, 3, 1, TournamentFormat.SINGLE_ELIMINATION, false, true)));

        ArgumentCaptor<CreateTournamentCommand> captor = ArgumentCaptor.forClass(CreateTournamentCommand.class);
        verify(tournamentRepository).create(captor.capture());
        assertThat(captor.getValue().settingsJson()).contains("\"teamSize\":3");
    }

    @Test
    void publishRejectsInvalidStatusTransition() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.LIVE,
                settings(),
                STARTS_AT)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> tournamentService.publishTournament(TOURNAMENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tournament cannot be published from status 'live'.");
    }

    @Test
    void duplicateSlugMapsToBadRequest() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.create(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate",
                new RuntimeException("duplicate key value violates unique constraint \"tournaments_slug_key\"")));

        assertThatThrownBy(() -> tournamentService.createTournament(new CreateTournamentRequest(
                "Mid Wars Open",
                "mid-wars-open",
                null,
                null,
                null,
                null,
                null,
                STARTS_AT,
                null,
                null,
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament slug is already in use.");
    }

    private static AuthenticatedActor actor(UUID profileId, ProfileRole role) {
        return new AuthenticatedActor(
                AUTH_USER_ID,
                profileId,
                "organizer@example.test",
                null,
                role);
    }

    private static TournamentSettings settings() {
        return new TournamentSettings(
                8,
                2,
                5,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                false,
                true);
    }

    private static Tournament tournament(
            TournamentStatus status,
            TournamentSettings settings,
            OffsetDateTime startsAt
    ) {
        return tournament(
                status,
                settings,
                startsAt,
                startsAt.minusDays(7),
                startsAt.minusDays(1),
                null,
                null);
    }

    private static Tournament tournament(
            TournamentStatus status,
            TournamentSettings settings,
            OffsetDateTime startsAt,
            OffsetDateTime registrationOpensAt,
            OffsetDateTime registrationClosesAt,
            OffsetDateTime checkInOpensAt,
            OffsetDateTime checkInClosesAt
    ) {
        return new Tournament(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                status,
                settings.format(),
                ORGANIZER_PROFILE_ID,
                "Organizer",
                "Qualifier",
                "Rules",
                "TBD",
                settings.maxTeams(),
                startsAt,
                startsAt.plusDays(1),
                registrationOpensAt,
                registrationClosesAt,
                status.isPublicVisible(),
                AUTH_USER_ID,
                "UTC",
                checkInOpensAt,
                checkInClosesAt,
                status == TournamentStatus.PUBLISHED ? OffsetDateTime.parse("2026-05-12T00:00:00Z") : null,
                settings,
                0,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-05-01T00:00:00Z"));
    }
}
